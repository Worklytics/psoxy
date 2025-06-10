package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.BucketOutputLocation;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import co.worklytics.psoxy.gateway.ProcessedContent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.extern.java.Log;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.logging.Level;

/**
 * S3-backed implementation of {@link Output}.
 */
@Log
public class S3Output implements Output {

    final String bucket;

    final String pathPrefix;

    @Inject
    Provider<AmazonS3> s3ClientProvider;

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
    public void write(String key, ProcessedContent content) {

        if (key == null) {
            key = DigestUtils.md5Hex(content.getContent());
        }


        try {
            AmazonS3 s3Client = s3ClientProvider.get();

            ObjectMetadata metadata = new ObjectMetadata();

            // s3 client blows up if these are filled with 'null' values, so only set if present
            Optional.ofNullable(content.getContentEncoding()).ifPresent(metadata::setContentEncoding);
            Optional.ofNullable(content.getContentType()).ifPresent(metadata::setContentType);

            metadata.setContentLength(content.getContent().length);  //explicit length avoids S3 complaint about buffering ...


            content.getMetadata().entrySet().stream()
                .filter(entry -> entry.getValue() != null) // avoid null values
                .forEach(entry -> metadata.addUserMetadata(entry.getKey(), entry.getValue()));

            s3Client.putObject(bucket,
                pathPrefix + key,
                new ByteArrayInputStream(content.getContent()),
                metadata);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to write to S3 output", e);
        }
    }

    @Override
    public void write(ProcessedContent content) {
        // Generate a canonical key based on the content's hash
        // random UUID better??
        String key = DigestUtils.sha256Hex(content.getContent());
        write(key, content);
    }
}
