package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Getter;

import java.io.InputStreamReader;

/**
 * Request received when a change is done in some storage service, such as "new" object created or "updated" object
 */
@Builder
@Getter
public class StorageEventRequest {
    /**
     * Stream where the object can be read
     */
    InputStreamReader readerStream;

    /**
     * Name of the bucket where the object is. Example: "mybucket"
     */
    String sourceBucketName;

    /**
     * Path without the bucket of the object. Example: "outputs/test/file.csv"
     */
    String sourceObjectPath;

    /**
     * If provided, the name of the destination bucket that can be used
     */
    String destinationBucket;
}
