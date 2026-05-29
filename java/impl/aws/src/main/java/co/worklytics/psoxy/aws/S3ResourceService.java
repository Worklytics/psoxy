package co.worklytics.psoxy.aws;

import java.io.InputStream;
import java.util.Optional;
import java.util.logging.Level;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import com.avaulta.gateway.resources.ResourceService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * ResourceService backed by AWS S3.
 *
 * <p>Fetches resources from a configured S3 bucket + path prefix.</p>
 */
@Log
@RequiredArgsConstructor
public class S3ResourceService implements ResourceService {

    @NonNull
    final S3Client s3Client;

    @NonNull
    final String bucketName;

    /**
     * Path prefix within the bucket (may be empty string for root).
     */
    @NonNull
    final String pathPrefix;

    @Override
    public Optional<InputStream> getResource(String objectPath) {
        ResourceService.validatePath(objectPath);
        String key = resolveKey(objectPath);
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
            return Optional.of(s3Client.getObject(request));
        } catch (NoSuchKeyException e) {
            log.log(Level.FINE, "S3 resource not found: s3://{0}/{1}", new Object[]{bucketName, key});
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                log.log(Level.FINE, "S3 resource not found: s3://{0}/{1}", new Object[]{bucketName, key});
                return Optional.empty();
            }
            log.log(Level.WARNING, "Error fetching S3 resource: s3://" + bucketName + "/" + key, e);
            throw e;
        }
    }

    private String resolveKey(String objectPath) {
        if (pathPrefix.isEmpty()) {
            return objectPath;
        }
        // avoid double slashes
        String prefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
        return prefix + objectPath;
    }
}
