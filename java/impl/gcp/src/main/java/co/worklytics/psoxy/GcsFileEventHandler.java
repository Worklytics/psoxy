package co.worklytics.psoxy;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;
import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.functions.Context;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.storage.StorageHandler;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

@Log
public class GcsFileEventHandler {

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

            // Sanitize to a temp file first so object metadata (including genMetadata token
            // aggregates) can be set when the GCS object is created — same pattern as S3Handler.
            File tmpFile = new File("/tmp/" + UUID.randomUUID());
            try {
                try (FileOutputStream fos = new FileOutputStream(tmpFile);
                     BufferedOutputStream outputStream =
                         new BufferedOutputStream(fos, storageHandler.getBufferSize())) {
                    storageHandler.handle(request, transform, inputStreamSupplier, () -> outputStream);
                }

                Map<String, String> metadata =
                    storageHandler.buildObjectMetadata(importBucket, sourceName, transform);

                BlobInfo.Builder blobInfoBuilder = BlobInfo.newBuilder(
                        BlobId.of(request.getDestinationBucketName(), request.getDestinationObjectPath()))
                    .setMetadata(metadata);

                Optional.ofNullable(request.getContentType())
                    .ifPresent(blobInfoBuilder::setContentType);

                if (request.getCompressOutput()) {
                    blobInfoBuilder.setContentEncoding(StorageHandler.CONTENT_ENCODING_GZIP);
                } else {
                    Optional.ofNullable(sourceBlobInfo.getContentEncoding())
                        .ifPresent(blobInfoBuilder::setContentEncoding);
                }

                try (FileInputStream processed = new FileInputStream(tmpFile);
                     WriteChannel writeChannel = storage.writer(blobInfoBuilder.build(),
                         Storage.BlobWriteOption.disableGzipContent());
                     OutputStream gcsOut = Channels.newOutputStream(writeChannel)) {
                    processed.transferTo(gcsOut);
                }
            } finally {
                if (tmpFile.exists() && !tmpFile.delete()) {
                    log.warning("Failed to delete temporary GCS output file: "
                        + tmpFile.getAbsolutePath());
                }
            }
        } else {
            log.info("Skipping " + importBucket + "/" + request.getSourceObjectPath() + " because no rules apply");
        }
    }
}
