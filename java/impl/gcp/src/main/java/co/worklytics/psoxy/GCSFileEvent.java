package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.storage.StorageHandler;
import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.java.Log;


import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.channels.Channels;
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

        BlobInfo sourceBlobInfo = storage.get(sourceBlobId);

        if (storageHandler.hasBeenSanitized(sourceBlobInfo.getMetadata())) {
            //possible if proxy directly (or indirectly via some other pipeline) is writing back
            //to the same bucket it originally read from. to avoid perpetuating the loop, skip
            log.warning("Skipping " + importBucket + "/" + sourceName + " because it has already been sanitized; does your configuration result in a loop?");
            return;
        }

        StorageEventRequest request = storageHandler.buildRequest(null, null, importBucket, sourceName, transform);

        boolean inputIsCompressed =
            StorageHandler.isCompressed(sourceBlobInfo.getContentEncoding());

        StorageHandler.warnIfEncodingDoesNotMatchFilename(request, sourceBlobInfo.getContentEncoding());

        // for now, just do the same for both
        request = request.withCompressOutput(inputIsCompressed).withDecompressInput(inputIsCompressed);

        if (storageHandler.getApplicableRules(transform.getRules(), request.getSourceObjectPath()).isPresent()) {
            BlobInfo destBlobInfo = BlobInfo.newBuilder(BlobId.of(request.getDestinationBucketName(), request.getDestinationObjectPath()))
                .setContentType(sourceBlobInfo.getContentType())
                .setMetadata(storageHandler.buildObjectMetadata(importBucket, sourceName, transform))
                .build();

            try (ReadChannel readChannel = storage.reader(sourceBlobId, Storage.BlobSourceOption.shouldReturnRawInputStream(true));
                 InputStream is = Channels.newInputStream(readChannel);
                 WriteChannel writeChannel = storage.writer(destBlobInfo);
                 OutputStream os = Channels.newOutputStream(writeChannel)) {
                storageHandler.process(request, transform, is, os);
            }
        } else {
            log.info("Skipping " + importBucket + "/" + request.getSourceObjectPath() + " because no rules apply");
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
