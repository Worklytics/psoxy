package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.test.TestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;

public class BulkDataTestUtils {


    /**
     * build a prototype request for testing
     * @param sourceObjectPath
     * @param filePath
     * @param writer
     * @return
     */
    public static StorageEventRequest request(String sourceObjectPath, String filePath, Writer writer) {
        Reader reader = new InputStreamReader(new ByteArrayInputStream(TestUtils.getData(filePath)));

        return StorageEventRequest.builder()
            .sourceBucketName("bucket")
            .sourceObjectPath(sourceObjectPath)
            .sourceReader(reader)
            .destinationWriter(writer)
            .destinationBucketName("bucket")
            .destinationObjectPath(sourceObjectPath)
            .build();
    }
}
