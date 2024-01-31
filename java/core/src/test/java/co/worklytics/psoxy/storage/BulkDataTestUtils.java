package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.rules.BulkDataRules;
import com.avaulta.gateway.rules.RuleSet;

import java.io.*;
import java.util.function.Supplier;

public class BulkDataTestUtils {


    public static Supplier<InputStream> inputStreamSupplier(String filePath) {
        return () -> new ByteArrayInputStream(TestUtils.getData(filePath));
    }

    /**
     * build a prototype request for testing
     * @param sourceObjectPath
     * @return
     */
    public static StorageEventRequest request(String sourceObjectPath) {
        return StorageEventRequest.builder()
            .sourceBucketName("bucket")
            .sourceObjectPath(sourceObjectPath)
            .destinationBucketName("bucket")
            .destinationObjectPath(sourceObjectPath)
            .build();
    }

    public static StorageHandler.ObjectTransform transform(RuleSet rules) {
        return StorageHandler.ObjectTransform.builder()
            .rules((BulkDataRules) rules)
            .destinationBucketName("test-bucket") //avoid NPE
            .build();
    }
}
