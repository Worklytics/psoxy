package co.worklytics.psoxy.gateway.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import co.worklytics.psoxy.gateway.BulkContentTypes;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.Output;
import lombok.extern.java.Log;

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

    public static final String GZIP_CONTENT_ENCODING = "gzip";

    // output
    OutputUtils outputUtils;

    @Inject
    public BatchMergeHandler(OutputUtils outputUtils) {
        this.outputUtils = Objects.requireNonNull(outputUtils, "outputUtils must not be null");
    }

    /**
     * atomically handle batch of webhooks, writing to output
     * 
     * ALL or NONE should be written to output
     * 
     * @param batch stream of webhooks to process
     */
    public void handleBatch(Stream<ProcessedContent> batch) {
        // Implementation for handling a batch of ProcessedContent
        // This could involve aggregating, transforming, or writing the content to an output
        // For example, you might write each item in the batch to a single output location
        // or perform some aggregation logic before writing.

        // combine into single processes content, call output.

        // create a gzipped stream of the batch content
        AtomicInteger rowCount = new AtomicInteger(0);
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {

            batch.forEach(item -> {
                
                if (item.getContentType() == null) {
                    throw new IllegalArgumentException("Batch items must have a content type");
                }
                if (!BulkContentTypes.MERGEABLE_JSON_RECORD_TYPES.contains(item.getContentType())) {
                    throw new IllegalArgumentException(
                        "Batch items must have one of the supported content types: "
                            + BulkContentTypes.describeContentTypes(BulkContentTypes.MERGEABLE_JSON_RECORD_TYPES)
                            + "; was " + item.getContentType());
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
                    rowCount.incrementAndGet();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            gzipOutputStream.finish();

            int finalRowCount = rowCount.get();
            if (rowCount.get() > 0) {
                ProcessedContent combined = ProcessedContent.builder()
                    .contentEncoding(GZIP_CONTENT_ENCODING)
                    .content(byteArrayOutputStream.toByteArray())
                    .contentType(BulkContentTypes.NDJSON.getMimeType()) // suggested, but not yet an official standard IANA type
                    .build();
                outputUtils.forBatchedWebhookContent().write(combined);
                
                log.log(Level.INFO, "Successfully processed batch with " + finalRowCount + " rows");
            } else {
                log.log(Level.INFO, "No rows successfully processed in batch");
            }
        } catch (Output.WriteFailure e) {
            log.log(Level.SEVERE, "Failed to write batched webhooks to output", e);
            throw new UncheckedIOException("Failed to write batched webhooks to output", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
