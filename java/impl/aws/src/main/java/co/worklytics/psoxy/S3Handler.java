package co.worklytics.psoxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
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
public class S3Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<S3Event, String> {

    @Inject
    StorageHandler storageHandler;

    @Inject
    S3Client s3Client;


    List<String> EXPECTED_CONTENT_TYPES = Arrays.asList(
        "text/csv; charset=utf-8",
        "text/csv; charset=ascii",
        "text/csv; charset=us-ascii"
        //"text/csv; charset=iso-8859-1" // standardized latin-1, don't expect this
    );



    @SneakyThrows
    @Override
    public String handleRequest(S3Event s3Event, Context context) {

        DaggerAwsContainer.create().injectS3Handler(this);

        S3EventNotification.S3EventNotificationRecord record = s3Event.getRecords().get(0);

        String importBucket = record.getS3().getBucket().getName();
        String sourceKey = record.getS3().getObject().getUrlDecodedKey();

        log.info(String.format("Received a request for processing %s from bucket %s.", sourceKey, importBucket));

        List<StorageHandler.ObjectTransform> transforms = storageHandler.buildTransforms();

        for (StorageHandler.ObjectTransform transform : transforms) {
            process(importBucket, sourceKey, transform);
        }

        return "Processed!";
    }

    @SneakyThrows
    StorageEventResponse process(String importBucket, String sourceKey, StorageHandler.ObjectTransform transform) {
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

        // check content type here
        if (sourceMetadata.contentType() != null
                && !EXPECTED_CONTENT_TYPES.contains(sourceMetadata.contentType().toLowerCase())) {
            // our code presumes a CSV, which is utf-8 encoded atm (or something like ascii, which is a subset of utf-8)
            log.warning(String.format("S3 file content type for %s/%s is %s (content encoding: %s); this is not known to be compatible with UTF-8-encoded CSVs, so may not work as expected",
                importBucket, sourceKey, sourceMetadata.contentType(), sourceMetadata.contentEncoding()));
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

            // set headers iff they're non-null on source object
            Optional.ofNullable(sourceMetadata.contentType())
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
