package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.SideOutput;
import co.worklytics.psoxy.gateway.impl.SideOutputUtils;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.InputStream;
import java.util.logging.Level;

@Log
public class GCSSideOutput implements SideOutput {

    static final int BUFFER_SIZE = 8192; // 8KB buffer size for writing

    final String bucket;

    @Inject
    Provider<Storage> storageProvider;

    @Inject
    SideOutputUtils sideOutputUtils;

    @AssistedInject
    public GCSSideOutput(@Assisted  String bucket) {
        this.bucket = bucket;
    }



    @Override
    public void write(HttpEventRequest request, ProcessedContent content) {
        try {
            Storage storageClient = storageProvider.get();

            try (WriteChannel writeChannel = storageClient.writer(
                    BlobInfo.newBuilder(bucket, sideOutputUtils.canonicalResponseKey(request))
                        .setContentType(content.getContentType())
                        .setContentEncoding("gzip")
                        .setMetadata(sideOutputUtils.buildMetadata(request))
                        .build());
                 InputStream gzippedStream = sideOutputUtils.toGzippedStream(content.getContent(), content.getContentCharset())
            ) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = gzippedStream.read(buffer)) != -1) {
                        writeChannel.write(java.nio.ByteBuffer.wrap(buffer, 0, bytesRead));
                    }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to write to GCS sideOutput", e);
            throw new RuntimeException(e);
        }
    }

    //TODO: could add a method to write pre-compressed content directly from a stream, to essentially pipe it w/o decompressing
    // but requires susing chunked encoding or something, which is not clear how to do
}
