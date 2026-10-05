package co.worklytics.psoxy;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;
import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.functions.Context;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.storage.StorageHandler;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

@Log
public class GcsFileEventHandler {

    static final int HTTP_FORBIDDEN = 403;

    final StorageHandler storageHandler;

    final Provider<Storage> storageProvider;


    @Inject
    public GcsFileEventHandler(StorageHandler storageHandler,
                               Provider<Storage> storageProvider) {
        this.storageHandler = storageHandler;
        this.storageProvider = storageProvider;
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

        Storage storage = storageProvider.get();
        BlobId sourceBlobId = BlobId.of(importBucket, sourceName);

        BlobInfo sourceBlobInfo = storage.get(sourceBlobId);

        if (storageHandler.hasBeenSanitized(sourceBlobInfo.getMetadata())) {
            //possible if proxy directly (or indirectly via some other pipeline) is writing back
            //to the same bucket it originally read from. to avoid perpetuating the loop, skip
            log.warning("Skipping " + importBucket + "/" + sourceName + " because it has already been sanitized; does your configuration result in a loop?");
            return;
        }

        StorageEventRequest request =
            storageHandler.buildRequest(importBucket, sourceName, transform, sourceBlobInfo.getContentEncoding(), sourceBlobInfo.getContentType());

        if (storageHandler.getApplicableRules(transform.getRules(), request.getSourceObjectPath()).isPresent()) {

            Supplier<InputStream> inputStreamSupplier = () -> {
                ReadChannel readChannel = storage.reader(sourceBlobId, Storage.BlobSourceOption.shouldReturnRawInputStream(true));
                return Channels.newInputStream(readChannel);
            };

            // Stream directly to GCS. Do not buffer the sanitized object on /tmp.
            Supplier<OutputStream> outputStreamSupplier = () -> {
                BlobInfo.Builder blobInfoBuilder = BlobInfo.newBuilder(BlobId.of(request.getDestinationBucketName(), request.getDestinationObjectPath()))
                    .setMetadata(storageHandler.buildObjectMetadata(importBucket, sourceName, transform));

                Optional.ofNullable(request.getContentType())
                    .ifPresent(blobInfoBuilder::setContentType);

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

            patchDestinationMetadata(
                storage,
                BlobId.of(request.getDestinationBucketName(), request.getDestinationObjectPath()),
                storageHandler.buildObjectMetadata(importBucket, sourceName, transform));
        } else {
            log.info("Skipping " + importBucket + "/" + request.getSourceObjectPath() + " because no rules apply");
        }
    }

    /**
     * After a streamed write, PATCH custom metadata (token totals are only known after sanitize).
     * Skips when there is nothing to add. A 403 is non-fatal: some deployments omit
     * {@code storage.objects.update} (BYO IAM / older role). Does not {@code get} the blob first,
     * so the writer role need not include {@code storage.objects.get}.
     */
    void patchDestinationMetadata(Storage storage, BlobId destination, Map<String, String> metadata) {
        if (!hasGenMetadataTokenKeys(metadata)) {
            return;
        }
        try {
            storage.update(BlobInfo.newBuilder(destination).setMetadata(metadata).build());
        } catch (StorageException e) {
            if (e.getCode() == HTTP_FORBIDDEN) {
                log.warning("Could not PATCH GCS object metadata at gs://"
                    + destination.getBucket() + "/" + destination.getName()
                    + " (need storage.objects.update); genMetadata token totals remain in logs only");
                return;
            }
            throw e;
        }
    }

    static boolean hasGenMetadataTokenKeys(Map<String, String> metadata) {
        return metadata.containsKey(StorageHandler.BulkMetaData.GEN_METADATA_CALLS.getMetaDataKey())
            || metadata.containsKey(StorageHandler.BulkMetaData.GEN_METADATA_INPUT_TOKENS.getMetaDataKey())
            || metadata.containsKey(StorageHandler.BulkMetaData.GEN_METADATA_OUTPUT_TOKENS.getMetaDataKey());
    }
}
