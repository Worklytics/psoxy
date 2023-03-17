package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.gateway.StorageEventResponse;
import co.worklytics.psoxy.rules.CsvRules;
import com.avaulta.gateway.rules.BulkDataRules;
import lombok.*;

import javax.inject.Inject;
import java.io.Serializable;

/**
 * solves a DaggerMissingBinding exception in tests
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class StorageHandler {

    @Inject
    ConfigService config;

    @Inject
    BulkDataSanitizerFactory bulkDataSanitizerFactory;

    @Inject
    Pseudonymizer pseudonymizer;

    @SneakyThrows
    public StorageEventResponse handle(StorageEventRequest request, BulkDataRules rules) {

        BulkDataSanitizer fileHandler = bulkDataSanitizerFactory.get(request.getSourceObjectPath());

        return StorageEventResponse.builder()
                .destinationBucketName(request.getDestinationBucketName())
                .bytes(fileHandler.sanitize(request.getReaderStream(), rules, pseudonymizer))
                .destinationObjectPath(request.getSourceObjectPath())
                .build();
    }

    @AllArgsConstructor(staticName = "of")
    @NoArgsConstructor
    @Data
    public static class ObjectTransform implements Serializable {

        private static final long serialVersionUID = 1L;

        @NonNull
        String destinationBucketName;

        @NonNull
        CsvRules rules;
    }
}
