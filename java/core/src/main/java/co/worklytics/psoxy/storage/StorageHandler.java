package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.rules.RulesUtils;
import com.avaulta.gateway.rules.BulkDataRules;
import com.avaulta.gateway.rules.MultiTypeBulkDataRules;
import com.avaulta.gateway.rules.PathTemplateUtils;
import com.avaulta.gateway.rules.RuleSet;
import com.google.common.annotations.VisibleForTesting;
import lombok.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    RuleSet defaultRuleSet;

    @Inject
    RulesUtils rulesUtils;

    @Inject
    HostEnvironment hostEnvironment;

    @Inject
    PathTemplateUtils pathTemplateUtils;

    @RequiredArgsConstructor
    public enum BulkMetaData {
        INSTANCE_ID,
        VERSION,
        ORIGINAL_OBJECT_KEY,

        //q: sha-1 of rules? discarded for now as don't really see utility; would be complicated to
        // map back to actual value for debugging
        ;

        // aws prepends `x-amz-meta-` to this; but per documentation, that's not visible via the
        // java client (eg, they add/remove it automatically); it is visible through AWS console
        //
        // gcp doesn't prepend anything
        static final String META_DATA_KEY_PREFIX = "psoxy-";


        public String getMetaDataKey() {
            return META_DATA_KEY_PREFIX + name().replace("_", "-").toLowerCase();
        }

    }

    @SneakyThrows
    public StorageEventResponse handle(StorageEventRequest request, RuleSet rules) {

        BulkDataRules applicableRules = getApplicableRules(rules, request.getSourceObjectPath());

        BulkDataSanitizer fileHandler = bulkDataSanitizerFactory.get(applicableRules);

        fileHandler.sanitize(request.getSourceReader(), request.getDestinationWriter(), pseudonymizer);

        return StorageEventResponse.builder()
                .destinationBucketName(request.getDestinationBucketName())
                .destinationObjectPath(request.getDestinationObjectPath())
                .build();
    }



    public StorageEventRequest buildRequest(Reader reader,
                                            Writer writer,
                                            String sourceBucketName,
                                            String sourceObjectPath,
                                            ObjectTransform transform) {

        String sourceObjectPathWithinBase =
            config.getConfigPropertyAsOptional(BulkModeConfigProperty.INPUT_BASE_PATH)
                .map(inputBasePath -> sourceObjectPath.replace(inputBasePath, ""))
                .orElse(sourceObjectPath);

        return StorageEventRequest.builder()
            .sourceReader(reader)
            .destinationWriter(writer)
            .sourceBucketName(sourceBucketName)
            .sourceObjectPath(sourceObjectPath)
            .destinationBucketName(transform.getDestinationBucketName())
            .destinationObjectPath(transform.getPathWithinBucket() + sourceObjectPathWithinBase)
            .build();
    }



    /**
     * @param sourceBucket the bucket from which the original object was read
     * @param sourceKey the key of the original object within the source bucket
     * @param transform the transform that was applied to the object
     *
     * @return metadata to be written to the transformed object
     */
    public Map<String, String> buildObjectMetadata(String sourceBucket, String sourceKey, ObjectTransform transform) {
        //transform currently unused; in future we probably want to record what transform was
        // applied, to aid traceability of pipelines
        return Map.of(
            BulkMetaData.INSTANCE_ID.getMetaDataKey(), hostEnvironment.getInstanceId(),
            BulkMetaData.VERSION.getMetaDataKey(), config.getConfigPropertyAsOptional(ProxyConfigProperty.BUNDLE_FILENAME).orElse("unknown"),
            BulkMetaData.ORIGINAL_OBJECT_KEY.getMetaDataKey(), sourceBucket + "/" + sourceKey
        );
    }

    /**
     * @return true if the object has already been sanitized by this proxy instance
     */
    public boolean hasBeenSanitized(Map<String, String> objectMeta) {
        // the instanceId check here allows for chaining proxies, so a loop is still possible in
        // such a scenario, if lambda 1 triggered from bucket A, writes to B, which triggers lambda
        // 2 to write back to A

        if (objectMeta == null) {
            //GCP seems to return null here, rather than an empty map
            return false;
        }

        return objectMeta.getOrDefault(BulkMetaData.INSTANCE_ID.getMetaDataKey(), "")
            .equals(hostEnvironment.getInstanceId());
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class ObjectTransform implements Serializable {

        private static final long serialVersionUID = 3L;

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

        @NonNull
        BulkDataRules rules;
    }


    @VisibleForTesting
    ObjectTransform buildDefaultTransform() {
        if (defaultRuleSet instanceof BulkDataRules) {
            return ObjectTransform.builder()
                .destinationBucketName(config.getConfigPropertyOrError(BulkModeConfigProperty.OUTPUT_BUCKET))
                .pathWithinBucket(config.getConfigPropertyAsOptional(BulkModeConfigProperty.OUTPUT_BASE_PATH).orElse(""))
                .rules((BulkDataRules) defaultRuleSet)
                .build();
        } else {
            throw new RuntimeException("Default rules are not BulkDataRules");
        }
    }


    private BulkDataRules getApplicableRules(RuleSet rules, String sourceObjectPath) {
        if (rules instanceof MultiTypeBulkDataRules) {
            return pathTemplateUtils.match(((MultiTypeBulkDataRules) rules).getFileRules(), sourceObjectPath)
                .orElseThrow(() -> new IllegalArgumentException("No matching rules for path: " + sourceObjectPath));
        } else if (rules instanceof BulkDataRules) {
            return (BulkDataRules) rules;
        } else {
            throw new RuntimeException("Rules are not BulkDataRules or MultiTypeBulkDataRules");
        }
    }


}
