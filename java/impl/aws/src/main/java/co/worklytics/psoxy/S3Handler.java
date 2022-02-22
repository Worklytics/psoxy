package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.gateway.StorageEventResponse;
import co.worklytics.psoxy.storage.StorageHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.io.input.BOMInputStream;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Log
public class S3Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<S3Event, String> {

    @Inject
    StorageHandler storageHandler;

    @Inject
    ConfigService configService;

    @Inject
    AmazonS3 s3Client;

    @SneakyThrows
    @Override
    public String handleRequest(S3Event s3Event, Context context) {

        DaggerAwsContainer.create().injectS3Handler(this);

        boolean isBOMEncoded = Boolean.parseBoolean(configService.getConfigPropertyAsOptional(AWSConfigProperty.BOM_ENCODED).orElse("false"));
        String destinationBucket = configService.getConfigPropertyAsOptional(AWSConfigProperty.OUTPUT_BUCKET)
                .orElseThrow(() -> new IllegalStateException("Output bucket not found as environment variable!"));

        String response = "200 OK";
        S3EventNotification.S3EventNotificationRecord record = s3Event.getRecords().get(0);

        String importBucket = record.getS3().getBucket().getName();
        String sourceKey = record.getS3().getObject().getUrlDecodedKey();

        log.info(String.format("Received a request for processing %s from bucket %s. BOM flag is marked as %s", sourceKey, importBucket, isBOMEncoded));

        S3Object s3Object = s3Client.getObject(new GetObjectRequest(importBucket, sourceKey));
        StorageEventResponse storageEventResponse;

        try(InputStream objectData = s3Object.getObjectContent()) {
            InputStreamReader reader;

            BOMInputStream is = null;
            if (isBOMEncoded) {
                is = new BOMInputStream(objectData);
                reader = new InputStreamReader(is, StandardCharsets.UTF_8.name());
            } else {
                reader = new InputStreamReader(objectData);
            }

            StorageEventRequest request = StorageEventRequest.builder()
                    .sourceBucketName(importBucket)
                    .sourceObjectPath(sourceKey)
                    .destinationBucket(destinationBucket)
                    .readerStream(reader)
                    .build();

           storageEventResponse = storageHandler.handle(request);

           if (isBOMEncoded) {
               is.close();
           }
           reader.close();
        }

        log.info("Writing to: " + storageEventResponse.getDestinationBucketName() + "/" + storageEventResponse.getDestinationObjectPath());

        try (InputStream is = new ByteArrayInputStream(storageEventResponse.getBytes())) {

            ObjectMetadata meta = new ObjectMetadata();

            meta.setContentLength(storageEventResponse.getBytes().length);
            meta.setContentType(s3Object.getObjectMetadata().getContentType());

            s3Client.putObject(storageEventResponse.getDestinationBucketName(),
                    storageEventResponse.getDestinationObjectPath(),
                    is,
                    meta);
        }

        log.info(String.format("Successfully pseudonymized %s/%s and uploaded to %s/%s",
                importBucket,
                sourceKey,
                storageEventResponse.getDestinationBucketName(),
                storageEventResponse.getDestinationObjectPath()));

        return response;
    }
}
