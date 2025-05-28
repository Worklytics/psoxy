package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.SideOutput;
import co.worklytics.psoxy.gateway.impl.SideOutputUtils;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.ByteArrayInputStream;
import java.util.logging.Level;

@Log
public class S3SideOutput implements SideOutput {

    final String bucket;
    final String pathPrefix;

    @Inject
    Provider<AmazonS3> s3ClientProvider;

    @Inject
    SideOutputUtils sideOutputUtils;

    @AssistedInject
    public S3SideOutput(@Assisted("bucket") String bucket,
                        @Assisted("pathPrefix") String pathPrefix) {
        if (StringUtils.isBlank(bucket)) {
            throw new IllegalArgumentException("Bucket name must not be blank");
        }
        this.bucket = bucket;
        String trimmedPath = StringUtils.trimToEmpty(pathPrefix);
        this.pathPrefix = (trimmedPath.endsWith("/") || StringUtils.isEmpty(trimmedPath)) ? trimmedPath : trimmedPath + "/";
    }

    @Override
    public void write(@NonNull HttpEventRequest request, @NonNull ProcessedContent content) {
        try {
            AmazonS3 s3Client = s3ClientProvider.get();

            byte[] compressedContent =
                sideOutputUtils.gzipContent(content.getContent(), content.getContentCharset());

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentEncoding("gzip");
            metadata.setContentType(content.getContentType());
            metadata.setUserMetadata(sideOutputUtils.buildMetadata(request));
            metadata.setContentLength(compressedContent.length);  //explicit length avoids S3 complaint about buffering ...

            s3Client.putObject(bucket,
                pathPrefix + sideOutputUtils.canonicalResponseKey(request),
                new ByteArrayInputStream(compressedContent),
                metadata);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to write to S3 sideOutput", e);
            throw new RuntimeException(e);
        }
    }

    //TODO: could add a method to write pre-compressed content directly from a stream, to essentially pipe it w/o decompressing
    // but requires susing chunked encoding or something, which is not clear how to do
}
