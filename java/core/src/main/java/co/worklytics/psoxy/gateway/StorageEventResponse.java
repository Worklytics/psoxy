package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class StorageEventResponse {
    byte[] bytes;

    String destinationBucketName;

    String destinationPath;
}
