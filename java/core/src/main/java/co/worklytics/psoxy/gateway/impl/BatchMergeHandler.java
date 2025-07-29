package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.Output;
import lombok.extern.java.Log;

import javax.inject.Inject;
import org.apache.hc.core5.http.ContentType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * takes a batch of processed data items and merges it into a single processed data item
 *
 * Some might call this a "Fan in"?
 *
 * TODO: this is actually a specific case; merge json/ndjson into ndjson
 * --> genericize that?
 *  --> do we care if people mix-up content types?
 *
 *
 *
 *
 */
@Log
public class BatchMergeHandler {

    // supported input content types
    // eg, stuff that can be concatenated into ndjson output
    // which is json, or other ndjson
    public static final Set<String> SUPPORTED_INPUT_CONTENT_TYPES = Set.of(
        ContentType.APPLICATION_JSON.getMimeType(),
        ContentType.APPLICATION_NDJSON.getMimeType()
    );

    public static final String GZIP_CONTENT_ENCODING = "gzip";

    // output
    OutputUtils outputUtils;

    @Inject
    public BatchMergeHandler(OutputUtils outputUtils) {
        this.outputUtils = Objects.requireNonNull(outputUtils, "outputUtils must not be null");
    }

    public void handleBatch(Stream<ProcessedContent> batch) {
        // Implementation for handling a batch of ProcessedContent
        // This could involve aggregating, transforming, or writing the content to an output
        // For example, you might write each item in the batch to a single output location
        // or perform some aggregation logic before writing.

        // combine into single processes content, call output.

        // create a gzipped stream of the batch content
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {


            batch.forEach(item -> {
                if (item.getContentType() == null) {
                    throw new IllegalArgumentException("Batch items must have a content type");
                }
                if (!SUPPORTED_INPUT_CONTENT_TYPES.contains(item.getContentType())) {
                    throw new IllegalArgumentException("Batch items must have content type 'application/json' or 'application/x-ndjson'; was " + item.getContentType());
                }
                byte[] uncompressedContent;
                if (GZIP_CONTENT_ENCODING.equals(item.getContentEncoding())) {
                    //decompress the content
                    try (
                        java.io.ByteArrayInputStream byteArrayInputStream = new java.io.ByteArrayInputStream(item.getContent());
                        java.util.zip.GZIPInputStream gzipInputStream = new java.util.zip.GZIPInputStream(byteArrayInputStream)
                    ) {
                        uncompressedContent = gzipInputStream.readAllBytes();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to decompress content", e);
                    }
                } else {
                    // if not gzip, assume it's already uncompressed
                    uncompressedContent = item.getContent();
                }
                // write each content item to the gzip output stream
                try {
                    gzipOutputStream.write(uncompressedContent);
                    gzipOutputStream.write('\n');
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            gzipOutputStream.finish();

            ProcessedContent combined = ProcessedContent.builder()
                .contentEncoding(GZIP_CONTENT_ENCODING)
                .content(byteArrayOutputStream.toByteArray())
                .contentType(ContentType.APPLICATION_NDJSON.getMimeType()) // suggested, but not yet an official standard IANA type
                .build();

            outputUtils.forBatchedWebhookContent().write(combined);
        } catch (Output.WriteFailure e) {
            log.log(Level.SEVERE, "Failed to write batched webhooks to output", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
