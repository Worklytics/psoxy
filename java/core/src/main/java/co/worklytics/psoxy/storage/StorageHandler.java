package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.RESTApiSanitizer;
import co.worklytics.psoxy.RESTApiSanitizerFactory;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.gateway.StorageEventResponse;
import co.worklytics.psoxy.rules.RuleSet;
import com.avaulta.gateway.rules.ColumnarRules;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
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
    FileHandlerFactory fileHandlerStrategy;

    @Inject
    Pseudonymizer pseudonymizer;

    @SneakyThrows
    public StorageEventResponse handle(StorageEventRequest request, ColumnarRules rules) {

        FileHandler fileHandler = fileHandlerStrategy.get(request.getSourceObjectPath());

        return StorageEventResponse.builder()
                .destinationBucketName(request.getDestinationBucketName())
                .bytes(fileHandler.handle(request.getReaderStream(), rules, pseudonymizer))
                .destinationObjectPath(request.getSourceObjectPath())
                .build();
    }

    @AllArgsConstructor(staticName = "of")
    @NoArgsConstructor
    @Data
    public static class ObjectTransform implements Serializable {

        @NonNull
        String destinationBucketName;

        @NonNull
        ColumnarRules rules;
    }
}
