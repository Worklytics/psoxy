package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

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
     * Stream to write output to
     */
    @NonNull
    OutputStreamWriter destinationStream;

    // REST OF THIS IS INFORMATION FOR SANITIZER TO INFORM SANITIZATION LOGIC BASED ON SOURCE/DESTINATION

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
