package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.gateway.BulkModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.gateway.StorageEventResponse;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.psoxy.storage.StorageHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.avaulta.gateway.rules.ColumnarRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.io.input.BOMInputStream;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Log
public class S3Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<S3Event, String> {

    @Inject
    StorageHandler storageHandler;

    @Inject
    ColumnarRules defaultRules;

    @Inject
    ConfigService configService;

    @Inject
    AmazonS3 s3Client;

    @Inject
    RulesUtils rulesUtils;

    @SneakyThrows
    @Override
    public String handleRequest(S3Event s3Event, Context context) {

        DaggerAwsContainer.create().injectS3Handler(this);

        String destinationBucket = configService.getConfigPropertyAsOptional(BulkModeConfigProperty.OUTPUT_BUCKET)
                .orElseThrow(() -> new IllegalStateException("Output bucket not found as environment variable!"));

        S3EventNotification.S3EventNotificationRecord record = s3Event.getRecords().get(0);

        String importBucket = record.getS3().getBucket().getName();
        String sourceKey = record.getS3().getObject().getUrlDecodedKey();

        log.info(String.format("Received a request for processing %s from bucket %s.", sourceKey, importBucket));


        List<StorageHandler.ObjectTransform> transforms = Lists.newArrayList(
            StorageHandler.ObjectTransform.of(destinationBucket,  defaultRules));

        rulesUtils.parseAdditionalTransforms(configService)
            .forEach(transforms::add);

        for (StorageHandler.ObjectTransform transform : transforms) {
            process(importBucket, sourceKey, transform.getDestinationBucketName(), transform.getRules());
        }

        return "Processed!";
    }

    @SneakyThrows
    StorageEventResponse process(String importBucket, String sourceKey, String destination, ColumnarRules rules) {
        StorageEventResponse storageEventResponse;
        S3Object s3Object = s3Client.getObject(new GetObjectRequest(importBucket, sourceKey));

        try (InputStream objectData = s3Object.getObjectContent();
             BOMInputStream is = new BOMInputStream(objectData);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            StorageEventRequest request = StorageEventRequest.builder()
                .sourceBucketName(importBucket)
                .sourceObjectPath(sourceKey)
                .destinationBucketName(destination)
                .readerStream(reader)
                .build();

            storageEventResponse = storageHandler.handle(request, rules);
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

        return storageEventResponse;
    }


}
