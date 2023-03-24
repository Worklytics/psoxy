package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.DaggerAwsContainer;

import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.storage.StorageHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.s3.AmazonS3;
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
import java.util.List;

@Log
public class S3Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<S3Event, String> {

    @Inject
    StorageHandler storageHandler;

    @Inject
    AmazonS3 s3Client;


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
        S3Object s3Object = s3Client.getObject(new GetObjectRequest(importBucket, sourceKey));

        if (storageHandler.hasBeenSanitized(s3Object.getObjectMetadata().getUserMetadata())) {
            //possible if proxy directly (or indirectly via some other pipeline) is writing back
            //to the same bucket it originally read from. to avoid perpetuating the loop, skip
            log.warning("Skipping " + importBucket + "/" + sourceKey + " because it has already been sanitized; does your configuration result in a loop?");
            return null;
        }

        try (InputStream objectData = s3Object.getObjectContent();
             BOMInputStream is = new BOMInputStream(objectData);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            StorageEventRequest request = storageHandler.buildRequest(reader, importBucket, sourceKey, transform);

            storageEventResponse = storageHandler.handle(request, transform.getRules());

            log.info("Writing to: " + request.getDestinationBucketName() + "/" + request.getDestinationObjectPath());
        }



        try (InputStream is = new ByteArrayInputStream(storageEventResponse.getBytes())) {

            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(storageEventResponse.getBytes().length);
            meta.setContentType(s3Object.getObjectMetadata().getContentType());

            meta.setUserMetadata(storageHandler.getObjectMeta(importBucket, sourceKey, transform));

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

        return storageEventResponse;
    }


}
