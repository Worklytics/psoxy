package co.worklytics.psoxy;

import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.Optional;
import java.util.logging.Level;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;

import co.worklytics.psoxy.gateway.ResourceService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * ResourceService backed by Google Cloud Storage.
 *
 * <p>Fetches resources from a configured GCS bucket + path prefix.</p>
 */
@Log
@RequiredArgsConstructor
public class GcsResourceService implements ResourceService {

    @NonNull
    final Storage storage;

    @NonNull
    final String bucketName;

    /**
     * Path prefix within the bucket (may be empty string for root).
     */
    @NonNull
    final String pathPrefix;

    @Override
    public Optional<InputStream> getResource(String objectPath) {
        String key = resolveKey(objectPath);
        BlobId blobId = BlobId.of(bucketName, key);

        try {
            if (storage.get(blobId) == null) {
                log.log(Level.FINE, "GCS resource not found: gs://{0}/{1}", new Object[]{bucketName, key});
                return Optional.empty();
            }

            ReadChannel readChannel = storage.reader(blobId);
            log.log(Level.INFO, "Loaded resource from GCS: gs://{0}/{1}", new Object[]{bucketName, key});
            return Optional.of(Channels.newInputStream(readChannel));
        } catch (StorageException e) {
            if (e.getCode() == 404) {
                log.log(Level.FINE, "GCS resource not found: gs://{0}/{1}", new Object[]{bucketName, key});
                return Optional.empty();
            }
            log.log(Level.WARNING, "Error fetching GCS resource: gs://" + bucketName + "/" + key, e);
            throw e;
    }

    private String resolveKey(String objectPath) {
        if (pathPrefix.isEmpty()) {
            return objectPath;
        }
        String prefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
        return prefix + objectPath;
    }
}
