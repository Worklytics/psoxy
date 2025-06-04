package co.worklytics.psoxy.gateway;

import java.io.IOException;
import java.util.Collection;

/**
 * an output interface for writing processed content
 *
 * example implementations could include:
 *  SQS, Pub/Sub, Cloud Storage, or a local file system
 *
 */
public interface Output {

    interface Options {

    }

    /**
     * writes processed content to this output
     *
     * @param content the processed content to write
     */
    void write(ProcessedContent content);

    /**
     * writes a batch of processed content to this output
     * left to implementation to decide how to handle batching, if at all; possibly combines into a single request / file / whatever
     *
     * alternatively, more conventional to have a 'flush' method or something??
     *
     * Issues:
     *   - really, implementations will combine multiple ProcessedContent instances of same content-type / encoding into a
     *     single ProcessedContent instance, with some sort of delimiter; none of that is exactly coupled to S3 vs GCS,
     *     although it is possibly implementation-specific for SQS/Pub/Sub, etc. -
     *
     * @param contents the collection of processed content to write
     */
    void batchWrite(Collection<ProcessedContent> contents);
}
