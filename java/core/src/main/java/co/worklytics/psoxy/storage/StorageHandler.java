package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.rules.RulesUtils;
import com.avaulta.gateway.rules.BulkDataRules;
import com.avaulta.gateway.rules.MultiTypeBulkDataRules;
import com.avaulta.gateway.rules.PathTemplateUtils;
import com.avaulta.gateway.rules.PathTemplateUtils.Match;
import com.avaulta.gateway.rules.RuleSet;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import lombok.*;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.Optional;

/**
 * solves a DaggerMissingBinding exception in tests
 */
@Log
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
    public StorageEventResponse process(StorageEventRequest request,
                 StorageHandler.ObjectTransform transform,
                 InputStreamReader reader,
                 OutputStream os) {
        try (OutputStream outputStream = request.getCompressOutput() ? new GZIPOutputStream(os) : os;
             OutputStreamWriter streamWriter = new OutputStreamWriter(outputStream);
             Writer writer= new BufferedWriter(streamWriter)) {

            request = request.withSourceReader(reader)
                .withDestinationWriter(writer);

            StorageEventResponse storageEventResponse = this.handle(request, transform.getRules());

            log.info("Writing to: " + storageEventResponse.getDestinationBucketName() + "/" + storageEventResponse.getDestinationObjectPath());

            log.info("Successfully pseudonymized " + request.getSourceBucketName() + "/"
                + request.getSourceObjectPath() + " and uploaded to " + storageEventResponse.getDestinationBucketName() + "/" + storageEventResponse.getDestinationObjectPath());
            return storageEventResponse;
        }

    }


    @SneakyThrows
    public StorageEventResponse handle(StorageEventRequest request, RuleSet rules) {

        Optional<BulkDataRules> applicableRules = getApplicableRules(rules, request.getSourceObjectPath());

        if (applicableRules.isEmpty()) {
            throw new IllegalArgumentException("No applicable rules found for " + request.getSourceObjectPath());
        }

        BulkDataSanitizer fileHandler = bulkDataSanitizerFactory.get(applicableRules.get());

        fileHandler.sanitize(request.getSourceReader(), request.getDestinationWriter(), pseudonymizer);

        return StorageEventResponse.builder()
                .destinationBucketName(request.getDestinationBucketName())
                .destinationObjectPath(request.getDestinationObjectPath())
                .build();
    }




    /**
     *
     * @param reader from source, may be null if not yet known
     * @param writer to destination, may be null if not yet known
     * @param sourceBucketName bucket name
     * @param sourceObjectPath a path (object key) within the bucket; no leading `/` expected
     * @param transform
     * @return
     */
    public StorageEventRequest buildRequest(Reader reader,
                                            Writer writer,
                                            String sourceBucketName,
                                            String sourceObjectPath,
                                            ObjectTransform transform) {

        String sourceObjectPathWithinBase =
            inputBasePath()
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


    public List<ObjectTransform> buildTransforms() {
        List<StorageHandler.ObjectTransform> transforms = new ArrayList<>();
        transforms.add(buildDefaultTransform());

        rulesUtils.parseAdditionalTransforms(config)
            .forEach(transforms::add);

        return transforms;
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
            BulkMetaData.ORIGINAL_OBJECT_KEY.getMetaDataKey(), sourceBucket + sourceKey
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
        return ObjectTransform.builder()
                .destinationBucketName(config.getConfigPropertyOrError(BulkModeConfigProperty.OUTPUT_BUCKET))
                .pathWithinBucket(outputBasePath().orElse(""))
                .rules(defaultRuleSet)
                .build();
    }


    /**
     * get applicable rules, if any
     *
     * @param rules
     * @param sourceObjectPath
     * @return applicable rules from rules, given sourceObjectPath - if it should be processed
     */
    @SneakyThrows
    public Optional<BulkDataRules> getApplicableRules(RuleSet rules, String sourceObjectPath) {
        BulkDataRules applicableRules = null;
        if (rules instanceof MultiTypeBulkDataRules) {
            Optional<Match<BulkDataRules>> match =
                pathTemplateUtils.matchVerbose(((MultiTypeBulkDataRules) rules).getFileRules(), sourceObjectPath);

            if (match.isPresent()) {
                if (match.get().getMatch() instanceof MultiTypeBulkDataRules) {
                    String subPath = match.get().getCapturedParams().get(match.get().getCapturedParams().size() - 1);
                    BulkDataRules nextMatch = pathTemplateUtils.match(((MultiTypeBulkDataRules) match.get().getMatch()).getFileRules(), subPath)
                        .orElse(null);
                    if (nextMatch instanceof MultiTypeBulkDataRules) {
                        throw new RuntimeException("MultiTypeBulkDataRules cannot be nested more than 1 level");
                    }
                    applicableRules = nextMatch;
                } else {
                    applicableRules = match.get().getMatch();
                }
            } else {
                applicableRules = null;
            }
        } else if (rules instanceof BulkDataRules) {
            applicableRules = (BulkDataRules) rules;
        } else {
            throw new RuntimeException("Rules are not BulkDataRules or MultiTypeBulkDataRules");
        }
        return Optional.ofNullable(applicableRules);
    }


    /**
     * helper to parse inputBasePath from config, if present;
     * left here to support potential logic change around presumption of leading `/`
     * @return
     */
    Optional<String> inputBasePath() {
        return config.getConfigPropertyAsOptional(BulkModeConfigProperty.INPUT_BASE_PATH)
            .filter(inputBasePath -> StringUtils.isNotBlank(inputBasePath));
            //.map(inputBasePath -> inputBasePath.startsWith("/") ? inputBasePath : "/" + inputBasePath);
    }

    Optional<String> outputBasePath() {
        return config.getConfigPropertyAsOptional(BulkModeConfigProperty.OUTPUT_BASE_PATH)
            .filter(outputBasePath -> StringUtils.isNotBlank(outputBasePath));
            //.map(outputBasePath -> outputBasePath.startsWith("/") ? outputBasePath : "/" + outputBasePath);
    }

}
