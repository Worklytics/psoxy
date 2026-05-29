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
 * <p>Fetches resources via {@code GetObject} only (never {@code ListBucket}). When the object is
 * absent or the caller lacks access, S3 may respond with 403 instead of 404; that response is
 * treated as unavailable (non-fatal) rather than propagated as an error.</p>
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
            if (e.statusCode() == 404 || isAccessDenied(e)) {
                log.log(Level.FINE, "S3 resource not found or not accessible: s3://{0}/{1}", new Object[]{bucketName, key});
                return Optional.empty();
            }
            log.log(Level.WARNING, "Error fetching S3 resource: s3://" + bucketName + "/" + key, e);
            throw e;
        }
    }

    /**
     * {@code true} when S3 denied access to the object. Callers may treat this as unavailable
     * rather than fatal — e.g. S3 often returns 403 (citing {@code s3:ListBucket}) instead of 404
     * when the object is absent and the caller lacks bucket list permission.
     */
    static boolean isAccessDenied(S3Exception e) {
        return e.statusCode() == 403;
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
