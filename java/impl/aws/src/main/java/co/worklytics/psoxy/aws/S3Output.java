package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.Output;
import co.worklytics.psoxy.gateway.ProcessedContent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.java.Log;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.logging.Level;

/**
 * S3-backed implementation of {@link Output}.
 */
@Log
public class S3Output implements Output {

    final Options options;

    @Inject
    Provider<AmazonS3> s3ClientProvider;

    @With
    @Builder
    @Value
    public static class Options implements Output.Options {

        String bucket;

        String pathPrefix;
    }

    @AssistedInject
    public S3Output(@Assisted Options options) {
        if (StringUtils.isBlank(options.getBucket())) {
            throw new IllegalArgumentException("Bucket name must not be blank");
        }

        String trimmedPath = StringUtils.trimToEmpty(options.getPathPrefix());
        this.options = options.withPathPrefix(trimmedPath.endsWith("/") || StringUtils.isEmpty(trimmedPath) ? trimmedPath : trimmedPath + "/");
    }


    @Override
    public void write(ProcessedContent content) {
        try {
            AmazonS3 s3Client = s3ClientProvider.get();

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentEncoding("gzip"); // TODO: use content.getContentEncoding() if available
            metadata.setContentType(content.getContentType());
            //metadata.setUserMetadata(sideOutputUtils.buildMetadata(request));
            metadata.setContentLength(content.getContent().length());  //explicit length avoids S3 complaint about buffering ...

            s3Client.putObject(options.getBucket(),
                options.getPathPrefix() + DigestUtils.md5Hex(content.getContent()),
                new ByteArrayInputStream(content.getContent().getBytes(StandardCharsets.UTF_8)),
                metadata);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to write to S3 sideOutput", e);
        }
    }

    @Override
    public void batchWrite(Collection<ProcessedContent> contents) {
        // dumb implementation of this. really, we expect  you to wrap S3Output with something that is opinionated on
        // how to batch writes; eg, knows how to batch multiple json ProcessedContent objects into a single ndjson ProcessedContent
        contents.forEach(this::write);
    }
}
