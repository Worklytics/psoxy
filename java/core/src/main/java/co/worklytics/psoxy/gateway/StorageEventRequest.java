package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.With;

/**
 * Request received when a change is done in some storage service, such as "new" object created or "updated" object
 */
@With
@Builder
@Getter
public class StorageEventRequest {

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

    /**
     * whether to decompress (gunzip) input
     */
    @Builder.Default
    Boolean decompressInput = false;

    /**
     * whether to compress (gzip) output
     */
    @Builder.Default
    Boolean compressOutput = false;

    String contentType;


}
