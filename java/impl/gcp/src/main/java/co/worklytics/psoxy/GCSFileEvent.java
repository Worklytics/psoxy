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
import java.util.Optional;
import java.util.function.Supplier;

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

        StorageEventRequest request =
            storageHandler.buildRequest(importBucket, sourceName, transform, sourceBlobInfo.getContentEncoding());

        if (storageHandler.getApplicableRules(transform.getRules(), request.getSourceObjectPath()).isPresent()) {

            Supplier<InputStream> inputStreamSupplier = () -> {
                ReadChannel readChannel = storage.reader(sourceBlobId, Storage.BlobSourceOption.shouldReturnRawInputStream(true));
                return Channels.newInputStream(readChannel);
            };

            Supplier<OutputStream> outputStreamSupplier = () -> {
                BlobInfo.Builder blobInfoBuilder = BlobInfo.newBuilder(BlobId.of(request.getDestinationBucketName(), request.getDestinationObjectPath()))
                    .setContentType(sourceBlobInfo.getContentType())
                    .setMetadata(storageHandler.buildObjectMetadata(importBucket, sourceName, transform));

                if (request.getCompressOutput()) {
                    blobInfoBuilder.setContentEncoding(StorageHandler.CONTENT_ENCODING_GZIP);
                } else {
                    Optional.ofNullable(sourceBlobInfo.getContentEncoding())
                        .ifPresent(blobInfoBuilder::setContentEncoding);
                }
                //NOTE: disableGzipContent() is important to avoid double compression
                WriteChannel writeChannel = storage.writer(blobInfoBuilder.build(), Storage.BlobWriteOption.disableGzipContent());
                //NOTE: when close() called on the stream, close is called on channel, so should be OK
                return Channels.newOutputStream(writeChannel);
            };

            storageHandler.handle(request, transform, inputStreamSupplier, outputStreamSupplier);
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
