package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Getter;

/**
 * Response generated after processing an StorageEventRequest
 */
@Builder
@Getter
public class StorageEventResponse {
    /**
     * Content of the response in UTF-8 bytes
     */
    byte[] bytes;

    /**
     * Name of the destination bucket can be used. Example: "output"
     */
    String destinationBucketName;

    /**
     * Path of the object path without the bucket name: "2022/01/01/test.csv"
     */
    String destinationObjectPath;
}
