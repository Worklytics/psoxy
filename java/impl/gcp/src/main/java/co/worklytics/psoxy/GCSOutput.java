package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.BucketOutputLocation;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.extern.java.Log;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.logging.Level;

@Log
public class GCSOutput implements Output {

    final String bucket;
    final String pathPrefix;

    @Inject
    Provider<Storage> storageProvider;

    @Inject
    OutputUtils outputUtils;

    @AssistedInject
    public GCSOutput(@Assisted OutputLocation location) {
        BucketOutputLocation bucketLocation = BucketOutputLocation.from(location.getUri());
        if (StringUtils.isBlank(bucketLocation.getBucket())) {
            throw new IllegalArgumentException("Bucket name must not be blank");
        }
        this.bucket = bucketLocation.getBucket();
        this.pathPrefix = OutputUtils.formatObjectPathPrefix(bucketLocation.getPathPrefix());
    }


    @Override
    public void write(String key, ProcessedContent content) {
        // Implementation for writing a single ProcessedContent to GCS
        // This would typically involve using the Google Cloud Storage client library
        // to upload the content to the specified bucket and path.

        if (key == null) {
            key = DigestUtils.md5Hex(content.getContent());
        }

        try {
            Storage storageClient = storageProvider.get();

            try (WriteChannel writeChannel = storageClient.writer(
                BlobInfo.newBuilder(bucket, pathPrefix + key)
                    .setContentType(content.getContentType())
                    .setContentEncoding(content.getContentEncoding())
                    //.setMetadata(outputUtils.buildMetadata(request))
                    .build())) {
                writeChannel.write(java.nio.ByteBuffer.wrap(content.getContent(), 0, content.getContent().length));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to write to GCS sideOutput", e);
            // TODO: configurable if this is fatal??
            // throw something an let common request handler do something?? (Eg, return an error header?? )
        }
    }

    @Override
    public void write(ProcessedContent content) {
        // Generate a canonical key for the response
        String key = DigestUtils.md5Hex(content.getContent());
        write(key, content);
    }

}
