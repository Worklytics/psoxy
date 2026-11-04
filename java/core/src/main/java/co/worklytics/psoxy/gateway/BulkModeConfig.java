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

        configService.getConfigPropertyAsOptional(BulkModeConfigProperty.PARALLEL_RECORD_PROCESSING)
            .map(Boolean::parseBoolean)
            .ifPresent(builder::parallelRecordProcessing);
        configService.getConfigPropertyAsOptional(BulkModeConfigProperty.PARALLEL_RECORD_PROCESSING_THREADS)
            .map(value -> ConfigService.parseIntValue(
                BulkModeConfigProperty.PARALLEL_RECORD_PROCESSING_THREADS, value))
            .ifPresent(builder::parallelRecordProcessingThreads);

        return builder.build();
    }

    /** Default worker count when {@link #parallelRecordProcessing} is enabled without an explicit thread count. */
    public static final int DEFAULT_PARALLEL_RECORD_PROCESSING_THREADS = 4;

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
     * When true, record-oriented bulk sanitization processes records concurrently but still writes
     * output in input order within a single file-handling request.
     */
    Boolean parallelRecordProcessing;

    /**
     * Size of the fixed thread pool used when {@link #parallelRecordProcessing} is enabled.
     */
    Integer parallelRecordProcessingThreads;

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

    public boolean isParallelRecordProcessingEnabled() {
        return Boolean.TRUE.equals(parallelRecordProcessing);
    }

    public Optional<Integer> getParallelRecordProcessingThreads() {
        return Optional.ofNullable(parallelRecordProcessingThreads);
    }

    public int getParallelRecordProcessingThreadsOrDefault() {
        return getParallelRecordProcessingThreads()
            .filter(threads -> threads > 0)
            .orElse(DEFAULT_PARALLEL_RECORD_PROCESSING_THREADS);
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
         * Output format to use when writing sanitized data.
         * Values: NDJSON, CSV, PARQUET, JSON_ARRAY
         */
        BULK_OUTPUT_FORMAT,

        /**
         * When {@code true}, record-oriented bulk files sanitize records in parallel within a
         * single invocation, preserving output record order.
         */
        PARALLEL_RECORD_PROCESSING,

        /**
         * Thread pool size for {@link #PARALLEL_RECORD_PROCESSING}. Defaults to
         * {@link BulkModeConfig#DEFAULT_PARALLEL_RECORD_PROCESSING_THREADS} when unset.
         */
        PARALLEL_RECORD_PROCESSING_THREADS;

        @Getter(onMethod_ = @Override)
        private SupportedSource supportedSource = SupportedSource.ENV_VAR;
    }
}
