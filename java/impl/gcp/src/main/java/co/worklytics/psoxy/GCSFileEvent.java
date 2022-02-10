package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.gateway.StorageEventResponse;
import co.worklytics.psoxy.gateway.impl.CommonRequestHandler;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.Data;
import org.apache.commons.io.input.BOMInputStream;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class GCSFileEvent implements BackgroundFunction<GCSFileEvent.GcsEvent> {

    @Inject
    CommonRequestHandler requestHandler;

    @Inject
    ConfigService configService;

    @Override
    public void accept(GcsEvent gcsEvent, Context context) throws Exception {
        DaggerGcpContainer.create().injectGCSEvent(this);

        // See https://cloud.google.com/functions/docs/calling/storage#event_types
        if (context.eventType().equals("google.storage.object.finalize")) {

            boolean isBOMEncoded = Boolean.parseBoolean(configService.getConfigPropertyAsOptional(GCPConfigProperty.BOM_ENCODED).orElse("false"));
            String destinationBucket = configService.getConfigPropertyAsOptional(GCPConfigProperty.OUTPUT_BUCKET)
                    .orElseThrow(() -> new IllegalStateException("Output bucket not found as environment variable!"));

            String importBucket = gcsEvent.getBucket();
            String sourceName = gcsEvent.getName();
            Storage storage = StorageOptions.getDefaultInstance().getService();
            BlobId sourceBlobId = BlobId.of(destinationBucket, sourceName);
            BlobInfo blobInfo = storage.get(sourceBlobId);
            InputStream objectData = new ByteArrayInputStream(storage.readAllBytes(null));
            InputStreamReader reader;

            if (isBOMEncoded) {
                BOMInputStream is = new BOMInputStream(objectData);
                reader = new InputStreamReader(is, StandardCharsets.UTF_8.name());
            } else {
                reader = new InputStreamReader(objectData);
            }

            StorageEventRequest request = StorageEventRequest.builder()
                    .sourceBucketName(importBucket)
                    .sourceBucketName(sourceName)
                    .destinationBucket(destinationBucket)
                    .readerStream(reader)
                    .build();

            StorageEventResponse storageEventResponse = requestHandler.handle(request);

            InputStream is = new ByteArrayInputStream(storageEventResponse.getBytes());

            System.out.println("Writing to: " + storageEventResponse.getDestinationBucketName() + "/" + storageEventResponse.getDestinationPath());

            storage.createFrom(BlobInfo.newBuilder(BlobId.of(storageEventResponse.getDestinationBucketName(), storageEventResponse.getDestinationPath()))
                    .setContentType(blobInfo.getContentType())
                    .build(), is);

            System.out.println("Successfully pseudonymized " + importBucket + "/"
                    + sourceName + " and uploaded to " + storageEventResponse.getDestinationBucketName() + "/" + storageEventResponse.getDestinationPath());
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
