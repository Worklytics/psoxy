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
import lombok.*;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * solves a DaggerMissingBinding exception in tests
 */
@Log
@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
public class StorageHandler {

    // as writing to remote storage, err on size of larger buffer
    private static final int DEFAULT_BUFFER_SIZE = 65_536; //64 KB

    private static final String CONTENT_ENCODING_GZIP = "gzip";

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

    static void warnIfEncodingDoesNotMatchFilename(@NonNull StorageEventRequest request, @Nullable String contentEncoding) {
        if (request.getSourceObjectPath().endsWith(".gz")
            && !StringUtils.equals(contentEncoding, CONTENT_ENCODING_GZIP)) {
            log.warning("Input filename ends with .gz, but 'Content-Encoding' metadata is not 'gzip'; is this correct? Decompression is based on object's 'Content-Encoding'");
        }
    }

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
    public StorageEventResponse handle(StorageEventRequest request,
                                       StorageHandler.ObjectTransform transform,
                                       Supplier<InputStream> inputStreamSupplier,
                                       Supplier<OutputStream> outputStreamSupplier) {


        boolean isValid = this.validate(request, transform, inputStreamSupplier);
        if (!isValid) {
            // generally, more specific exception should have been thrown by validate
            throw new IllegalArgumentException("File is not valid");
        }

        this.process(request, transform, inputStreamSupplier, outputStreamSupplier);

        StorageEventResponse response = StorageEventResponse.builder()
            .destinationBucketName(request.getDestinationBucketName())
            .destinationObjectPath(request.getDestinationObjectPath())
            .build();

        log.info("Writing to: " + response.getDestinationBucketName() + "/" + response.getDestinationObjectPath());

        log.info("Successfully pseudonymized " + request.getSourceBucketName() + "/"
            + request.getSourceObjectPath() + " and uploaded to " + response.getDestinationBucketName() + "/" + response.getDestinationObjectPath());

        return response;
    }




    /**
     * @param sourceBucketName      bucket name
     * @param sourceObjectPath      a path (object key) within the bucket; no leading `/` expected
     * @param transform
     * @param sourceContentEncoding
     * @return
     */
    public StorageEventRequest buildRequest(String sourceBucketName,
                                            String sourceObjectPath,
                                            ObjectTransform transform,
                                            String sourceContentEncoding) {

        String sourceObjectPathWithinBase =
            inputBasePath()
                .map(inputBasePath -> sourceObjectPath.replace(inputBasePath, ""))
                .orElse(sourceObjectPath);


        StorageEventRequest request = StorageEventRequest.builder()
            .sourceBucketName(sourceBucketName)
            .sourceObjectPath(sourceObjectPath)
            .destinationBucketName(transform.getDestinationBucketName())
            .destinationObjectPath(transform.getPathWithinBucket() + sourceObjectPathWithinBase)
            .build();

        warnIfEncodingDoesNotMatchFilename(request, sourceContentEncoding);

        return request;
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



    //TODO: better name for this?
    // the rules are a 'transform', but the `destinationBucketName`/`pathWithinBucket' aren't
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
            if (!(((MultiTypeBulkDataRules) rules).getFileRules() instanceof LinkedHashMap)) {
                log.warning("File rules are not ordered; this may lead to unexpected behavior if templates are ambiguous");
            }

            Optional<Match<BulkDataRules>> match =
                pathTemplateUtils.matchVerbose(effectiveTemplates(((MultiTypeBulkDataRules) rules).getFileRules()), sourceObjectPath);

            if (match.isPresent()) {
                if (match.get().getMatch() instanceof MultiTypeBulkDataRules) {
                    log.warning("Nested MultiTypeBulkDataRules are very 'alpha', so may change in future versions!!");
                    String subPath = match.get().getCapturedParams().get(match.get().getCapturedParams().size() - 1);
                    Map<String, BulkDataRules> nextLevel = ((MultiTypeBulkDataRules) match.get().getMatch()).getFileRules();
                    BulkDataRules nextMatch = pathTemplateUtils.match(effectiveTemplates(nextLevel), subPath).orElse(null);
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
     * validate that the input stream can be processed per request/transform
     *
     * use-case: avoid opening output writer if file isn't valid
     *
     * @param request that triggered this processing
     * @param transform to apply (rules + output target)
     * @param inputStreamSupplier to get a stream of the input
     *
     * @throws Exception if file is invalid/processing failed
     */
    @SneakyThrows
    void validate(StorageEventRequest request,
                  StorageHandler.ObjectTransform transform,
                  Supplier<InputStream> inputStreamSupplier) {
        int bufferSize = getBufferSize();
        try (
            InputStream decompressedStream = request.getDecompressInput() ? new GZIPInputStream(inputStreamSupplier.get(), bufferSize) : inputStreamSupplier.get();
            BufferedReader reader = new BufferedReader(new InputStreamReader(decompressedStream, StandardCharsets.UTF_8), bufferSize);
            OutputStream out = new ByteArrayOutputStream()
        ) {

            StringBuffer firstLines = new StringBuffer();
            for (int lineCount = 0; lineCount < 5; lineCount++) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                firstLines.append(line);
            }

            Supplier<InputStream> firstLinesSupplier = () -> new ByteArrayInputStream(firstLines.toString().getBytes());

            this.process(request, transform, firstLinesSupplier, () -> out);
        }
    }


    private int getBufferSize() {
        return config.getConfigPropertyAsOptional(BulkModeConfigProperty.BUFFER_SIZE).map(Integer::parseInt).orElse(DEFAULT_BUFFER_SIZE);
    }

    /**
     * actually process the full input, getting inputStream and writing to outputStream
     *
     * @param request
     * @param transform
     * @param inputStreamSupplier
     * @param outputStreamSupplier
     */
    @SneakyThrows
    void process(StorageEventRequest request,
                 StorageHandler.ObjectTransform transform,
                 Supplier<InputStream> inputStreamSupplier,
                 Supplier<OutputStream> outputStreamSupplier) {
        int bufferSize = getBufferSize();

        try (
            InputStream decompressedStream = request.getDecompressInput() ? new GZIPInputStream(inputStreamSupplier.get(), bufferSize) : inputStreamSupplier.get();
            Reader reader = new BufferedReader(new InputStreamReader(decompressedStream, StandardCharsets.UTF_8), bufferSize);
            OutputStream outputStream = request.getCompressOutput() ? new GZIPOutputStream(outputStreamSupplier.get(), bufferSize) : outputStreamSupplier.get();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream)
        ) {

            Optional<BulkDataRules> applicableRules =
                getApplicableRules(transform.getRules(), request.getSourceObjectPath());

            if (applicableRules.isEmpty()) {
                throw new IllegalArgumentException("No applicable rules found for " + request.getSourceObjectPath());
            }

            BulkDataSanitizer fileHandler = bulkDataSanitizerFactory.get(applicableRules.get());

            fileHandler.sanitize(reader, writer, pseudonymizer);
        }

    }


    Map<String, BulkDataRules> effectiveTemplates(Map<String, BulkDataRules> original) {
        return original.entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().startsWith("/") ? entry.getKey().substring(1) : entry.getKey(),
                entry -> entry.getValue(),
                (a, b) -> a,
                LinkedHashMap::new //preserve order
            ));
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
