package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

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

    @NonNull
    InputStreamReader readerStream;

    /**
     * Name of the bucket where the object is. Example: "mybucket"
     */

    @NonNull
    String sourceBucketName;

    /**
     * Path without the bucket of the object. Example: "outputs/test/file.csv"
     */

    @NonNull
    String sourceObjectPath;

    /**
     * Name of the bucket where the object will be written. Example: "mybucket"
     */
    @NonNull
    String destinationBucketName;

    /**
     * Path tow the bucket of the object. Example: "outputs/test/file.csv"
     */
    @NonNull
    String destinationObjectPath;


}
