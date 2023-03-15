package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.BulkModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.gateway.StorageEventResponse;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.psoxy.storage.StorageHandler;
import com.avaulta.gateway.rules.ColumnarRules;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.io.input.BOMInputStream;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GCSFileEvent implements BackgroundFunction<GCSFileEvent.GcsEvent> {

    @Inject
    StorageHandler storageHandler;

    @Inject
    ColumnarRules defaultRuleSet;
    @Inject
    ConfigService configService;

    @Inject
    RulesUtils rulesUtils;

    @Override
    public void accept(GcsEvent gcsEvent, Context context) throws Exception {
        DaggerGcpContainer.create().injectGCSEvent(this);

        // See https://cloud.google.com/functions/docs/calling/storage#event_types
        if (context.eventType().equals("google.storage.object.finalize")) {

            String destinationBucket = configService.getConfigPropertyAsOptional(BulkModeConfigProperty.OUTPUT_BUCKET)
                    .orElseThrow(() -> new IllegalStateException("Output bucket not found as environment variable!"));

            String importBucket = gcsEvent.getBucket();
            String sourceName = gcsEvent.getName();


            List<StorageHandler.ObjectTransform> transforms = new ArrayList<>();
            transforms.add(StorageHandler.ObjectTransform.of(destinationBucket, defaultRuleSet));

            rulesUtils.parseAdditionalTransforms(configService)
                .forEach(transforms::add);

            for (StorageHandler.ObjectTransform transform : transforms) {
                process(importBucket, sourceName, transform);
            }
        }
    }


    @SneakyThrows
    void process(String importBucket, String sourceName, StorageHandler.ObjectTransform objectTransform) {

        Storage storage = StorageOptions.getDefaultInstance().getService();
        BlobId sourceBlobId = BlobId.of(importBucket, sourceName);
        BlobInfo blobInfo = storage.get(sourceBlobId);
        try (InputStream objectData = new ByteArrayInputStream(storage.readAllBytes(sourceBlobId));
             BOMInputStream is = new BOMInputStream(objectData);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            StorageEventRequest request = StorageEventRequest.builder()
                .sourceBucketName(importBucket)
                .sourceObjectPath(sourceName)
                .destinationBucketName(objectTransform.getDestinationBucketName())
                .readerStream(reader)
                .build();

            StorageEventResponse storageEventResponse = storageHandler.handle(request, objectTransform.getRules());

            try (InputStream processedStream = new ByteArrayInputStream(storageEventResponse.getBytes())) {

                System.out.println("Writing to: " + storageEventResponse.getDestinationBucketName() + "/" + storageEventResponse.getDestinationObjectPath());

                storage.createFrom(BlobInfo.newBuilder(BlobId.of(storageEventResponse.getDestinationBucketName(), storageEventResponse.getDestinationObjectPath()))
                    .setContentType(blobInfo.getContentType())
                    .build(), processedStream);

                System.out.println("Successfully pseudonymized " + importBucket + "/"
                    + sourceName + " and uploaded to " + storageEventResponse.getDestinationBucketName() + "/" + storageEventResponse.getDestinationObjectPath());
            }
        }
    }


    /**
     * See https://github.com/GoogleCloudPlatform/java-docs-samples/tree/460f5cffd9f8df09146947515458f336881e29d8/functions/helloworld/hello-gcs/src/main/java/functions
     * and https://cloud.google.com/functions/docs/writing/background#cloud-storage-example
     *
     * Original code include more fields that are not currently required
     */
    @Data
    public static class GcsEvent {
        // Cloud Functions uses GSON to populate this object.
        // Field types/names are specified by Cloud Functions
        // Changing them may break your code!
        private String bucket;
        private String name;
        private String metageneration;
    }
}
