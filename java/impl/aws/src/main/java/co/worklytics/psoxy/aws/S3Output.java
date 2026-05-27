package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.BucketOutputLocation;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import co.worklytics.psoxy.gateway.ProcessedContent;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.extern.java.Log;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * S3-backed implementation of {@link Output}.
 */
@Log
public class S3Output implements Output {

    final String bucket;

    final String pathPrefix;

    @Inject
    Provider<S3Client> s3ClientProvider;

    @AssistedInject
    public S3Output(@Assisted OutputLocation location) {
        BucketOutputLocation bucketLocation = BucketOutputLocation.from(location.getUri());

        if (StringUtils.isBlank(bucketLocation.getBucket())) {
            throw new IllegalArgumentException("Bucket name must not be blank");
        }


        this.bucket = bucketLocation.getBucket();
        this.pathPrefix = OutputUtils.formatObjectPathPrefix(bucketLocation.getPathPrefix());
    }


    @Override
    public void write(String key, ProcessedContent content) throws WriteFailure {

        if (key == null) {
            key = DigestUtils.md5Hex(content.getContent());
        }

        try {
            S3Client s3Client = s3ClientProvider.get();

            Map<String, String> userMetadata = new HashMap<>();

            content.getMetadata().entrySet().stream()
                .filter(entry -> entry.getValue() != null) // avoid null values
                .forEach(entry -> userMetadata.put(entry.getKey(), entry.getValue()));

            PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(pathPrefix + key)
                .contentLength((long) content.getContent().length)
                .metadata(userMetadata);

            // s3 client blows up if these are filled with 'null' values, so only set if present
            Optional.ofNullable(content.getContentEncoding()).ifPresent(putBuilder::contentEncoding);
            Optional.ofNullable(content.getContentType()).ifPresent(putBuilder::contentType);

            s3Client.putObject(putBuilder.build(), RequestBody.fromBytes(content.getContent()));
        } catch (Exception e) {
            throw new WriteFailure("Failed to write to S3 output", e);
        }
    }

    @Override
    public void write(ProcessedContent content) throws WriteFailure {
        // Generate a canonical key based on the content's hash
        // random UUID better??
        String key = DigestUtils.sha256Hex(content.getContent());
        write(key, content);
    }
}
