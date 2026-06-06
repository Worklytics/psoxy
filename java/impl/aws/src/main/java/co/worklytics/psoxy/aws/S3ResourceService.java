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
 * <p>Fetches resources via {@code GetObject}. Missing resources are treated as unavailable, but
 * access-denied responses are fatal so callers do not silently fall back to weaker defaults when
 * remote resource IAM is misconfigured.</p>
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
            if (isAccessDenied(e)) {
                log.log(Level.WARNING, "Access denied fetching S3 resource: s3://" + bucketName + "/" + key, e);
                throw e;
            }
            log.log(Level.WARNING, "Error fetching S3 resource: s3://" + bucketName + "/" + key, e);
            throw e;
        }
    }

    /**
     * {@code true} when S3 denied access to the object. Terraform grants prefix-scoped
     * {@code s3:ListBucket} for remote-resource paths so absent objects can be reported as 404.
     */
    static boolean isAccessDenied(S3Exception e) {
        return e.statusCode() == 403;
    }

    private String resolveKey(String objectPath) {
        String prefix = ResourceService.normalizeObjectKeyPrefix(pathPrefix);
        if (prefix.isEmpty()) {
            return objectPath;
        }
        // avoid double slashes
        String withSlash = prefix.endsWith("/") ? prefix : prefix + "/";
        return withSlash + objectPath;
    }
}
