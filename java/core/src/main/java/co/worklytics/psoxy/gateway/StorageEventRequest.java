package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Getter;

import java.io.InputStreamReader;

@Builder
@Getter
public class StorageEventRequest {
    InputStreamReader readerStream;

    String sourceBucketName;

    String sourceBucketPath;

    String destinationBucket;
}
