package co.worklytics.psoxy.gateway;

import co.worklytics.psoxy.storage.StorageHandler;
import com.avaulta.gateway.rules.RecordRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;

/**
 * POJO collecting all configuration values for bulk mode
 */
@Value
@Builder
public class BulkModeConfig {

    /**
     * Default worker count when legacy parallel enable flags are set without an explicit thread count.
     */
    public static final int DEFAULT_PARALLEL_PROCESSING_THREADS = 4;

    /** Upper bound for {@link BulkModeConfigProperty#PARALLEL_PROCESSING_THREADS}. */
    public static final int MAX_PARALLEL_PROCESSING_THREADS = 128;

    /**
     * Default bulk file-handling budget when {@link BulkModeConfigProperty#BULK_PROCESSING_TIMEOUT_SECONDS}
     * is unset; should match typical bulk Lambda / Cloud Function timeouts in Terraform modules.
     */
    public static final int DEFAULT_BULK_PROCESSING_TIMEOUT_SECONDS = 600;

    private static final int BULK_PROCESSING_TIMEOUT_HEADROOM_SECONDS = 30;

    /**
     * Factory method to build config from ConfigService
     */
    public static BulkModeConfig fromConfigService(ConfigService configService, ObjectMapper yamlMapper) {
        BulkModeConfigBuilder builder = BulkModeConfig.builder();

        configService.getConfigPropertyAsOptional(BulkModeConfigProperty.BULK_OUTPUT_FORMAT)
            .map(RecordRules.Format::valueOf)
            .ifPresent(builder::outputFormat);

        builder.outputBucket(configService.getConfigPropertyOrError(BulkModeConfigProperty.OUTPUT_BUCKET));
        configService.getConfigPropertyAsOptional(BulkModeConfigProperty.INPUT_BASE_PATH)
            .ifPresent(builder::inputBasePath);
        configService.getConfigPropertyAsOptional(BulkModeConfigProperty.OUTPUT_BASE_PATH)
            .ifPresent(builder::outputBasePath);
        configService.getConfigPropertyAsOptional(BulkModeConfigProperty.BUFFER_SIZE)
            .map(value -> ConfigService.parseIntValue(BulkModeConfigProperty.BUFFER_SIZE, value))
            .ifPresent(builder::bufferSize);
        configService.getConfigPropertyAsOptional(BulkModeConfigProperty.COMPRESS_OUTPUT_ALWAYS)
            .map(Boolean::parseBoolean)
            .ifPresent(builder::compressOutputAlways);

        configService.getConfigPropertyAsOptional(BulkModeConfigProperty.ADDITIONAL_TRANSFORMS)
            .ifPresent(yaml -> builder.additionalTransforms(parseAdditionalTransforms(yaml, yamlMapper)));

        resolveParallelProcessingThreads(configService).ifPresent(builder::parallelProcessingThreads);

        configService.getConfigPropertyAsOptional(BulkModeConfigProperty.BULK_PROCESSING_TIMEOUT_SECONDS)
            .map(value -> ConfigService.parseIntValue(
                BulkModeConfigProperty.BULK_PROCESSING_TIMEOUT_SECONDS, value))
            .ifPresent(builder::bulkProcessingTimeoutSeconds);

        return builder.build();
    }

    static Optional<Integer> resolveParallelProcessingThreads(ConfigService configService) {
        Optional<String> threadsValue = configService.getConfigPropertyAsOptional(
                BulkModeConfigProperty.PARALLEL_PROCESSING_THREADS)
            .or(() -> configService.getConfigPropertyAsOptional(
                BulkModeConfigProperty.PARALLEL_RECORD_PROCESSING_THREADS));
        if (threadsValue.isPresent()) {
            String trimmed = threadsValue.get().trim();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            BulkModeConfigProperty propertyUsed = configService.getConfigPropertyAsOptional(
                    BulkModeConfigProperty.PARALLEL_PROCESSING_THREADS).isPresent()
                ? BulkModeConfigProperty.PARALLEL_PROCESSING_THREADS
                : BulkModeConfigProperty.PARALLEL_RECORD_PROCESSING_THREADS;
            int threads = ConfigService.parseIntValue(propertyUsed, trimmed);
            if (threads <= 0) {
                throw new IllegalArgumentException(
                    propertyUsed.name() + " must be a positive integer, got: " + threads);
            }
            if (threads > MAX_PARALLEL_PROCESSING_THREADS) {
                throw new IllegalArgumentException(
                    propertyUsed.name() + " must be <= " + MAX_PARALLEL_PROCESSING_THREADS
                        + ", got: " + threads);
            }
            return Optional.of(threads);
        }

        boolean legacyEnabled = configService.getConfigPropertyAsOptional(
                BulkModeConfigProperty.PARALLEL_PROCESSING)
            .map(Boolean::parseBoolean)
            .orElse(false)
            || configService.getConfigPropertyAsOptional(BulkModeConfigProperty.PARALLEL_RECORD_PROCESSING)
            .map(Boolean::parseBoolean)
            .orElse(false);
        if (legacyEnabled) {
            return Optional.of(DEFAULT_PARALLEL_PROCESSING_THREADS);
        }
        return Optional.empty();
    }

    private static List<StorageHandler.ObjectTransform> parseAdditionalTransforms(String yaml,
                                                                                    ObjectMapper yamlMapper) {
        CollectionType type = yamlMapper.getTypeFactory()
            .constructCollectionType(ArrayList.class, StorageHandler.ObjectTransform.class);
        try {
            return yamlMapper.readValue(yaml, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse ADDITIONAL_TRANSFORMS from config", e);
        }
    }

    /**
     * Output format to use when writing sanitized data.
     * If not present, the input format should be used.
     */
    RecordRules.Format outputFormat;

    String outputBucket;
    String inputBasePath;
    String outputBasePath;
    Integer bufferSize;
    Boolean compressOutputAlways;
    @Builder.Default
    List<StorageHandler.ObjectTransform> additionalTransforms = Collections.emptyList();

    /**
     * When set, {@link RecordRules} bulk sanitization uses this many worker threads and still writes
     * output in input order within a single file-handling request.
     */
    Integer parallelProcessingThreads;

    /**
     * Overall file-handling budget in seconds; should match the bulk connector execution timeout
     * configured in infrastructure ({@link BulkModeConfigProperty#BULK_PROCESSING_TIMEOUT_SECONDS}).
     */
    @Builder.Default
    int bulkProcessingTimeoutSeconds = DEFAULT_BULK_PROCESSING_TIMEOUT_SECONDS;

    public Optional<RecordRules.Format> getOutputFormat() {
        return Optional.ofNullable(outputFormat);
    }

    public String getOutputBucketOrError() {
        if (outputBucket == null || outputBucket.isBlank()) {
            throw new IllegalStateException("Missing required bulk config: " + BulkModeConfigProperty.OUTPUT_BUCKET);
        }
        return outputBucket;
    }

    public Optional<String> getInputBasePath() {
        return Optional.ofNullable(inputBasePath);
    }

    public Optional<String> getOutputBasePath() {
        return Optional.ofNullable(outputBasePath);
    }

    public Optional<Integer> getBufferSize() {
        return Optional.ofNullable(bufferSize);
    }

    public Optional<Boolean> getCompressOutputAlways() {
        return Optional.ofNullable(compressOutputAlways);
    }

    public List<StorageHandler.ObjectTransform> getAdditionalTransforms() {
        return additionalTransforms;
    }

    public Optional<Integer> getParallelProcessingThreads() {
        return Optional.ofNullable(parallelProcessingThreads);
    }

    /**
     * Time budget for parallel record processing within a file, in seconds. Shorter than
     * {@link #getBulkProcessingTimeoutSeconds()} so the invocation can fail before the runtime
     * hard-kills the function.
     */
    public int getParallelProcessingBudgetSeconds() {
        return Math.max(1, bulkProcessingTimeoutSeconds - BULK_PROCESSING_TIMEOUT_HEADROOM_SECONDS);
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public enum BulkModeConfigProperty implements ConfigService.ConfigProperty {

        OUTPUT_BUCKET,

        /**
         * additional transforms to apply to each input file
         * @see co.worklytics.psoxy.storage.StorageHandler.ObjectTransform
         */
        ADDITIONAL_TRANSFORMS(SupportedSource.ENV_VAR_OR_REMOTE),

        INPUT_BASE_PATH,

        OUTPUT_BASE_PATH,

        BUFFER_SIZE,

        COMPRESS_OUTPUT_ALWAYS,

        /**
         * Bulk file-handling execution timeout in seconds; should match the Lambda / Cloud Function
         * timeout configured for the bulk connector instance.
         */
        BULK_PROCESSING_TIMEOUT_SECONDS,

        /**
         * Output format to use when writing sanitized data.
         * Values: NDJSON, CSV, PARQUET, JSON_ARRAY
         */
        BULK_OUTPUT_FORMAT,

        /**
         * When set to a positive integer (max {@link BulkModeConfig#MAX_PARALLEL_PROCESSING_THREADS}),
         * {@link RecordRules} bulk files sanitize records in parallel within a single invocation,
         * preserving output record order.
         */
        PARALLEL_PROCESSING_THREADS,

        /**
         * Deprecated alias for {@link #PARALLEL_PROCESSING_THREADS}.
         */
        PARALLEL_RECORD_PROCESSING_THREADS,

        /**
         * Legacy: when {@code true} and no thread-count property is set, enables parallel processing
         * with {@link BulkModeConfig#DEFAULT_PARALLEL_PROCESSING_THREADS} threads.
         */
        PARALLEL_PROCESSING,

        /**
         * Deprecated alias for {@link #PARALLEL_PROCESSING}.
         */
        PARALLEL_RECORD_PROCESSING;

        @Getter(onMethod_ = @Override)
        private SupportedSource supportedSource = SupportedSource.ENV_VAR;
    }
}
