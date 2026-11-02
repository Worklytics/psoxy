package co.worklytics.psoxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.newrelic.opentracing.LambdaTracer;
import io.opentracing.util.GlobalTracer;
import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.gateway.StorageEventResponse;
import co.worklytics.psoxy.storage.StorageHandler;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Log
public class S3Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<S3Event, String> {

    /**
     * Static initialization allows reuse in containers
     * {@link "https://aws.amazon.com/premiumsupport/knowledge-center/lambda-improve-java-function-performance/"}
     */
    static final Duration CONTAINER_CACHE_TTL = Duration.ofMinutes(20);

    static final Object DEPENDENCIES_LOCK = new Object();

    static volatile CachedDependencies dependencies;

    static boolean newRelicInitialized;

    static class CachedDependencies {
        final StorageHandler storageHandler;
        final S3Client s3Client;
        final String configFingerprint;
        final long loadedAtNanos;

        CachedDependencies(StorageHandler storageHandler, S3Client s3Client, String configFingerprint,
                           long loadedAtNanos) {
            this.storageHandler = storageHandler;
            this.s3Client = s3Client;
            this.configFingerprint = configFingerprint;
            this.loadedAtNanos = loadedAtNanos;
        }

        boolean isValid(String expectedFingerprint, long nowNanos) {
            return Objects.equals(configFingerprint, expectedFingerprint)
                && nowNanos - loadedAtNanos < CONTAINER_CACHE_TTL.toNanos();
        }
    }

    @Inject StorageHandler injectedStorageHandler;
    @Inject S3Client injectedS3Client;

    static String configFingerprint() {
        return Objects.toString(System.getenv(ProxyConfigProperty.CUSTOM_RULES_SHA.name()), "")
            + "|" + Objects.toString(System.getenv(ProxyConfigProperty.BUNDLE_FILENAME.name()), "");
    }

    static CachedDependencies loadDependencies(String fingerprint, long loadedAtNanos) {
        AwsContainer awsContainer = DaggerAwsContainer.create();
        S3Handler bootstrap = new S3Handler();
        awsContainer.injectS3Handler(bootstrap);

        if (awsContainer.loggingConfiguration().isNewRelicEnabled() && !newRelicInitialized) {
            awsContainer.loggingConfiguration().validateNewRelicHandler(S3Handler.class);
            GlobalTracer.registerIfAbsent(LambdaTracer.INSTANCE);
            newRelicInitialized = true;
        }

        return new CachedDependencies(
            bootstrap.injectedStorageHandler,
            bootstrap.injectedS3Client,
            fingerprint,
            loadedAtNanos);
    }

    static CachedDependencies cachedDependencies() {
        String fingerprint = configFingerprint();
        long now = System.nanoTime();
        CachedDependencies current = dependencies;
        if (current != null && current.isValid(fingerprint, now)) {
            return current;
        }
        synchronized (DEPENDENCIES_LOCK) {
            current = dependencies;
            if (current != null && current.isValid(fingerprint, now)) {
                return current;
            }
            dependencies = loadDependencies(fingerprint, now);
            return dependencies;
        }
    }

    @SneakyThrows
    @Override
    public String handleRequest(S3Event s3Event, Context context) {

        CachedDependencies deps = cachedDependencies();

        S3EventNotification.S3EventNotificationRecord record = s3Event.getRecords().get(0);

        String importBucket = record.getS3().getBucket().getName();
        String sourceKey = record.getS3().getObject().getUrlDecodedKey();

        log.info(String.format("Received a request for processing %s from bucket %s.", sourceKey, importBucket));

        List<StorageHandler.ObjectTransform> transforms = deps.storageHandler.buildTransforms();

        for (StorageHandler.ObjectTransform transform : transforms) {
            process(deps, importBucket, sourceKey, transform);
        }

        return "Processed!";
    }

    @SneakyThrows
    StorageEventResponse process(CachedDependencies deps, String importBucket, String sourceKey,
                                 StorageHandler.ObjectTransform transform) {
        StorageHandler storageHandler = deps.storageHandler;
        S3Client s3Client = deps.s3Client;
        StorageEventResponse storageEventResponse;

        HeadObjectResponse sourceMetadata = s3Client.headObject(HeadObjectRequest.builder()
            .bucket(importBucket)
            .key(sourceKey)
            .build());


        //avoid potential npe should objectMetadata be null (if that can even happen?)
        Map<String, String> userMetadata = Optional.ofNullable(sourceMetadata)
            .map(HeadObjectResponse::metadata).orElse(Collections.emptyMap());

        if (storageHandler.hasBeenSanitized(userMetadata)) {
            //possible if proxy directly (or indirectly via some other pipeline) is writing back
            //to the same bucket it originally read from. to avoid perpetuating the loop, skip
            log.warning("Skipping " + importBucket + "/" + sourceKey + " because it has already been sanitized; does your configuration result in a loop?");
            return null;
        }

        StorageEventRequest request =
            storageHandler.buildRequest(importBucket, sourceKey, transform, sourceMetadata.contentEncoding(), sourceMetadata.contentType());

        // AWS lambdas have a shared ephemeral storage (shared across invocations) of 512MB
        // This can be upped to 10GB, but this should be enough as long as we're not processing
        // lots of large files in parallel.
        // https://aws.amazon.com/blogs/aws/aws-lambda-now-supports-up-to-10-gb-ephemeral-storage/
        File tmpFile = new File("/tmp/" + UUID.randomUUID());
        try (FileOutputStream fos = new FileOutputStream(tmpFile);
             BufferedOutputStream outputStream = new BufferedOutputStream(fos, storageHandler.getBufferSize())) {


            storageEventResponse = storageHandler.handle(request, transform, () -> {
                return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(importBucket)
                    .key(sourceKey)
                    .build());
            }, () -> outputStream);

            log.info(String.format("Successfully pseudonymized %s/%s to buffer", importBucket, sourceKey));
        }

        try (InputStream fileInputStream = new FileInputStream(tmpFile);
            BufferedInputStream processedStream = new BufferedInputStream(fileInputStream, storageHandler.getBufferSize())) {

            Map<String, String> destinationUserMetadata = storageHandler.buildObjectMetadata(importBucket, sourceKey, transform);

            PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                .bucket(storageEventResponse.getDestinationBucketName())
                .key(storageEventResponse.getDestinationObjectPath())
                .contentLength(tmpFile.length())
                .metadata(destinationUserMetadata);

            Optional.ofNullable(request.getContentType())
                .ifPresent(putBuilder::contentType);

            if (request.getCompressOutput()) {
                putBuilder.contentEncoding(StorageHandler.CONTENT_ENCODING_GZIP);
            } else {
                Optional.ofNullable(sourceMetadata.contentEncoding())
                    .ifPresent(putBuilder::contentEncoding);
            }

            s3Client.putObject(putBuilder.build(), RequestBody.fromInputStream(processedStream, tmpFile.length()));

            log.info(String.format("Successfully uploaded to %s/%s",
                storageEventResponse.getDestinationBucketName(),
                storageEventResponse.getDestinationObjectPath()));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmpFile.delete();
        }

        return storageEventResponse;
    }

}
