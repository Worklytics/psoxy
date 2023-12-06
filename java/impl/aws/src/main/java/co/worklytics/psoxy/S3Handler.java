package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.DaggerAwsContainer;

import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.storage.StorageHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectMetadataRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.io.input.BOMInputStream;

import javax.inject.Inject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

        ObjectMetadata sourceMetadata = s3Client.getObjectMetadata(importBucket, sourceKey);


        //avoid potential npe should objectMetadata be null (if that can even happen?)
        Map<String, String> userMetadata = Optional.ofNullable(sourceMetadata)
            .map(ObjectMetadata::getUserMetadata).orElse(Collections.emptyMap());

        if (storageHandler.hasBeenSanitized(userMetadata)) {
            //possible if proxy directly (or indirectly via some other pipeline) is writing back
            //to the same bucket it originally read from. to avoid perpetuating the loop, skip
            log.warning("Skipping " + importBucket + "/" + sourceKey + " because it has already been sanitized; does your configuration result in a loop?");
            return null;
        }

        S3Object sourceObject = s3Client.getObject(new GetObjectRequest(importBucket, sourceKey));
        byte[] processedData = null;
        try (InputStream objectData = sourceObject.getObjectContent();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            StorageEventRequest request = storageHandler.buildRequest(null, null, importBucket, sourceKey, transform);

            boolean isCompressed = Optional.ofNullable(sourceObject.getObjectMetadata().getContentEncoding())
                .map(s -> s.contains("gzip"))
                .orElse(false);

            request = request.withDecompressInput(isCompressed).withCompressOutput(isCompressed);

            storageEventResponse = storageHandler.process(request, transform, objectData, outputStream);

            processedData = outputStream.toByteArray();
            log.info(String.format("Successfully pseudonymized %s/%s to buffer", importBucket, sourceKey));
        }

        try (InputStream processedStream = new ByteArrayInputStream(processedData)) {
            ObjectMetadata meta = new ObjectMetadata();
            //NOTE: not setting content length here causes S3 client to buffer the stream ... OK
            //meta.setContentLength(storageEventResponse.getBytes().length);

            // set headers iff they're non-null on source object
            Optional.ofNullable(sourceObject.getObjectMetadata().getContentType())
                .ifPresent(meta::setContentType);
            Optional.ofNullable(sourceObject.getObjectMetadata().getContentEncoding())
                .ifPresent(meta::setContentEncoding);


            meta.setUserMetadata(storageHandler.buildObjectMetadata(importBucket, sourceKey, transform));


            s3Client.putObject(storageEventResponse.getDestinationBucketName(),
                storageEventResponse.getDestinationObjectPath(),
                processedStream,
                meta);

            log.info(String.format("Successfully uploaded to %s/%s",
                importBucket,
                sourceKey,
                storageEventResponse.getDestinationBucketName(),
                storageEventResponse.getDestinationObjectPath()));
        }

        return storageEventResponse;
    }

}
