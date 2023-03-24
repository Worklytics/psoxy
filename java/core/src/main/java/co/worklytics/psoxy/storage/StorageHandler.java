package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.gateway.BulkModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.gateway.StorageEventResponse;
import co.worklytics.psoxy.rules.CsvRules;
import co.worklytics.psoxy.rules.RulesUtils;
import com.avaulta.gateway.rules.BulkDataRules;
import lombok.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * solves a DaggerMissingBinding exception in tests
 */
@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
public class StorageHandler {

    @Inject
    ConfigService config;

    @Inject
    BulkDataSanitizerFactory bulkDataSanitizerFactory;

    @Inject
    Pseudonymizer pseudonymizer;

    @Inject
    BulkDataRules defaultRuleSet;

    @Inject
    RulesUtils rulesUtils;

    @SneakyThrows
    public StorageEventResponse handle(StorageEventRequest request, BulkDataRules rules) {

        BulkDataSanitizer fileHandler = bulkDataSanitizerFactory.get(request.getSourceObjectPath());

        return StorageEventResponse.builder()
                .bytes(fileHandler.sanitize(request.getReaderStream(), rules, pseudonymizer))
                .destinationBucketName(request.getDestinationBucketName())
                .destinationObjectPath(request.getDestinationObjectPath())
                .build();
    }

    public StorageEventRequest buildRequest(InputStreamReader reader, String sourceBucketName, String sourceObjectPath, StorageHandler.ObjectTransform transform) {

        String sourceObjectPathWithinBase =
            config.getConfigPropertyAsOptional(BulkModeConfigProperty.INPUT_BASE_PATH)
                .map(inputBasePath -> sourceObjectPath.replace(inputBasePath, ""))
                .orElse(sourceObjectPath);

        return StorageEventRequest.builder()
            .readerStream(reader)
            .sourceBucketName(sourceBucketName)
            .sourceObjectPath(sourceObjectPath)
            .destinationBucketName(transform.getDestinationBucketName())
            .destinationObjectPath(transform.getPathWithinBucket() + sourceObjectPathWithinBase)
            .build();
    }

    public List<ObjectTransform> buildTransforms() {
        List<StorageHandler.ObjectTransform> transforms = new ArrayList<>();
        transforms.add(buildDefaultTransform());

        rulesUtils.parseAdditionalTransforms(config)
            .forEach(transforms::add);

        return transforms;
    }

    ObjectTransform buildDefaultTransform() {
        return ObjectTransform.builder()
            .destinationBucketName(config.getConfigPropertyOrError(BulkModeConfigProperty.OUTPUT_BUCKET))
            .pathWithinBucket(config.getConfigPropertyAsOptional(BulkModeConfigProperty.OUTPUT_BASE_PATH).orElse(""))
            .rules((CsvRules) defaultRuleSet)
            .build();
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class ObjectTransform implements Serializable {

        private static final long serialVersionUID = 2L;

        /**
         * destination bucket in which to write the transformed object
         */
        @NonNull
        String destinationBucketName;

        /**
         * path within the destination bucket in which to write the transformed object, prepended
         * the object's original path within the source bucket.
         *
         * if destinationBucketName is the same as the source bucket, then NOT specifying this
         * will overwrite your original file - so make sure that's what you intend
         */
        @NonNull
        @Builder.Default
        String pathWithinBucket = "";

        //NOTE: need a concrete type here to serialize to/from YAML
        //TODO: support proper jackson polymorphism here, across potential BulkDataRules implementations

        @NonNull
        CsvRules rules;
    }
}
