package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.SideOutput;
import co.worklytics.psoxy.gateway.impl.SideOutputUtils;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.logging.Level;

@Log
public class GCSSideOutput implements SideOutput {

    final String bucket;
    final String pathPrefix;

    @Inject
    Provider<Storage> storageProvider;

    @Inject
    SideOutputUtils sideOutputUtils;

    @AssistedInject
    public GCSSideOutput(@Assisted("bucket") String bucket,
                         @Assisted("pathPrefix") String pathPrefix) {
        if (StringUtils.isBlank(bucket)) {
            throw new IllegalArgumentException("Bucket name must not be blank");
        }
        this.bucket = bucket;
        String trimmedPath = StringUtils.trimToEmpty(pathPrefix);
        // Ensure the path prefix ends with a slash, if non-empty
        this.pathPrefix = (trimmedPath.endsWith("/") || StringUtils.isEmpty(trimmedPath)) ? trimmedPath : trimmedPath + "/";
    }


    @Override
    public void write(@NonNull HttpEventRequest request, @NonNull ProcessedContent content) {
        try {
            Storage storageClient = storageProvider.get();

            try (WriteChannel writeChannel = storageClient.writer(
                    BlobInfo.newBuilder(bucket, pathPrefix + sideOutputUtils.canonicalResponseKey(request))
                        .setContentType(content.getContentType())
                        .setContentEncoding(content.getContentEncoding())
                        .setMetadata(sideOutputUtils.buildMetadata(request))
                        .build())) {
                writeChannel.write(java.nio.ByteBuffer.wrap(content.getContent(), 0, content.getContent().length));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to write to GCS sideOutput", e);
            // TODO: configurable if this is fatal??
            // throw something an let common request handler do something?? (Eg, return an error header?? )
        }
    }

    //TODO: could add a method to write pre-compressed content directly from a stream, to essentially pipe it w/o decompressing
    // but requires susing chunked encoding or something, which is not clear how to do
}
