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
        this.pathPrefix = SideOutputUtils.formatObjectPathPrefix(pathPrefix);
    }

    @Override
    public void write(@NonNull HttpEventRequest request, @NonNull ProcessedContent content) {
        try {
            AmazonS3 s3Client = s3ClientProvider.get();

            //q: correct to do the compression *inside* of every SideOutput implementation?
            //q: why not have Content-Encoding on ProcessedContent, compress conditionally?
            // not plainly wrong ... leaving it to the implementation to decide allows:
            //  - SideOutput to choose if it cares about compression, or just OK writing uncompressed (compression worthless in the `NoSideOutput`case)
            //  - SIdeOutput to compress while streaming, if it cares.

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentEncoding(content.getContentEncoding());
            metadata.setContentType(content.getContentType());
            metadata.setUserMetadata(sideOutputUtils.buildMetadata(request));  //q: why isn't this passed in on the ProcessedContent.metadata??
            metadata.setContentLength(content.getContent().length);  //explicit length avoids S3 complaint about buffering ...

            s3Client.putObject(bucket,
                pathPrefix + sideOutputUtils.canonicalResponseKey(request),
                new ByteArrayInputStream(content.getContent()),
                metadata);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to write to S3 sideOutput", e);
        }
    }

    //TODO: could add a method to write pre-compressed content directly from a stream, to essentially pipe it w/o decompressing
    // but requires susing chunked encoding or something, which is not clear how to do
}
