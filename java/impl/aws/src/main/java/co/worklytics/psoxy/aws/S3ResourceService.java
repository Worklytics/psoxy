package co.worklytics.psoxy.aws;

import java.io.InputStream;
import java.util.Optional;
import java.util.logging.Level;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import co.worklytics.psoxy.gateway.ResourceService;
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
    final AmazonS3 s3Client;

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
        try {
            if (!s3Client.doesObjectExist(bucketName, key)) {
                log.log(Level.FINE, "S3 resource not found: s3://{0}/{1}", new Object[]{bucketName, key});
                return Optional.empty();
            }
            S3Object s3Object = s3Client.getObject(new GetObjectRequest(bucketName, key));
            log.log(Level.INFO, "Loaded resource from S3: s3://{0}/{1}", new Object[]{bucketName, key});
            return Optional.of(s3Object.getObjectContent());
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404 || "NoSuchKey".equals(e.getErrorCode())) {
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
