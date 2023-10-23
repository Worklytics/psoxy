package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.With;

import java.io.Reader;
import java.io.Writer;

/**
 * Request received when a change is done in some storage service, such as "new" object created or "updated" object
 */
@With
@Builder
@Getter
public class StorageEventRequest {
    /**
     * reader to read input from
     */
    Reader sourceReader;

    /**
     *  to write output to
     */
    Writer destinationWriter;

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
