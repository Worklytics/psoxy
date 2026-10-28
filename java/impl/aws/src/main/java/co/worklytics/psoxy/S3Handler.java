package co.worklytics.psoxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import com.amazonaws.services.lambda.runtime.Context;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
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
public class S3Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<Map<String, Object>, String> {

    @Inject
    StorageHandler storageHandler;

    @Inject
    S3Client s3Client;


    @SneakyThrows
    @Override
    public String handleRequest(Map<String, Object> input, Context context) {

        DaggerAwsContainer.create().injectS3Handler(this);

        S3BulkObjectCreatedEvent objectCreated = S3BulkObjectCreatedEvent.fromLambdaEvent(input);

        String importBucket = objectCreated.getBucketName();
        String sourceKey = objectCreated.getObjectKey();
        String objectPath = StorageHandler.formatObjectPath(importBucket, sourceKey);

        log.info("Triggered for object " + objectPath);

        List<StorageHandler.ObjectTransform> transforms = storageHandler.buildTransforms();

        for (StorageHandler.ObjectTransform transform : transforms) {
            process(importBucket, sourceKey, objectPath, transform);
        }

        return "Processed!";
    }

    @SneakyThrows
    StorageEventResponse process(String importBucket, String sourceKey, String objectPath, StorageHandler.ObjectTransform transform) {
        if (storageHandler.getApplicableRules(transform.getRules(), sourceKey).isEmpty()) {
            log.info("skipped " + objectPath + " - no applicable rules");
            return null;
        }

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

            if (storageEventResponse == null) {
                return null;
            }

            log.info(String.format("Successfully pseudonymized %s to buffer", objectPath));
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
