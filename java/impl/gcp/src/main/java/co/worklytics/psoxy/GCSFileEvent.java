package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.gateway.StorageEventResponse;
import co.worklytics.psoxy.storage.StorageHandler;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import org.apache.commons.io.input.BOMInputStream;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Singleton
@Log
public class GCSFileEvent implements BackgroundFunction<GCSFileEvent.GcsEvent> {

    @Inject
    StorageHandler storageHandler;

    @Override
    public void accept(GcsEvent gcsEvent, Context context) throws Exception {
        DaggerGcpContainer.create().injectGCSEvent(this);

        // See https://cloud.google.com/functions/docs/calling/storage#event_types
        if (context.eventType().equals("google.storage.object.finalize")) {

            List<StorageHandler.ObjectTransform> transforms =
                storageHandler.buildTransforms();

            for (StorageHandler.ObjectTransform transform : transforms) {
                process(gcsEvent.getBucket(), gcsEvent.getName(), transform);
            }
        }
    }


    @SneakyThrows
    void process(String importBucket, String sourceName, StorageHandler.ObjectTransform transform) {

        Storage storage = StorageOptions.getDefaultInstance().getService();
        BlobId sourceBlobId = BlobId.of(importBucket, sourceName);
        BlobInfo blobInfo = storage.get(sourceBlobId);

        if (storageHandler.hasBeenSanitized(blobInfo.getMetadata())) {
            //possible if proxy directly (or indirectly via some other pipeline) is writing back
            //to the same bucket it originally read from. to avoid perpetuating the loop, skip
            log.warning("Skipping " + importBucket + "/" + sourceName + " because it has already been sanitized; does your configuration result in a loop?");
            return;
        }

        try (InputStream objectData = new ByteArrayInputStream(storage.readAllBytes(sourceBlobId));
             BOMInputStream is = new BOMInputStream(objectData);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {


            StorageEventRequest request = storageHandler.buildRequest(reader, importBucket, sourceName, transform);

            StorageEventResponse storageEventResponse = storageHandler.handle(request, transform.getRules());

            try (InputStream processedStream = new ByteArrayInputStream(storageEventResponse.getBytes())) {


                log.info("Writing to: " + storageEventResponse.getDestinationBucketName() + "/" + storageEventResponse.getDestinationObjectPath());


                storage.createFrom(
                    BlobInfo.newBuilder(BlobId.of(storageEventResponse.getDestinationBucketName(), storageEventResponse.getDestinationObjectPath()))
                        .setContentType(blobInfo.getContentType())
                        .setMetadata(storageHandler.getObjectMeta(importBucket, sourceName, transform))
                        .build(),
                    processedStream);


                log.info("Successfully pseudonymized " + importBucket + "/"
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
