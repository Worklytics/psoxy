package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.storage.StorageHandler;
import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.functions.Context;

import lombok.SneakyThrows;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Log
public class GcsFileEventHandler {

    final StorageHandler storageHandler;

    @Inject
    public GcsFileEventHandler(StorageHandler storageHandler) {
        this.storageHandler = storageHandler;
    }

    public void process(GCSFileEvent.GcsEvent event, Context context) {

        // See https://cloud.google.com/functions/docs/calling/storage#event_types
        if (context.eventType().equals("google.storage.object.finalize")) {

            List<StorageHandler.ObjectTransform> transforms =
                storageHandler.buildTransforms();

            for (StorageHandler.ObjectTransform transform : transforms) {
                process(event.getBucket(), event.getName(), transform);
            }
        } else {
            log.warning("Unsupported event type: " + context.eventType());
        }
    }


    @SneakyThrows
    private void process(String importBucket, String sourceName, StorageHandler.ObjectTransform transform) {

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
}
