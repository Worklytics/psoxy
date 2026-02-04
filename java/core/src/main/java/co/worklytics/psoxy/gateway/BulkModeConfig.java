package co.worklytics.psoxy.gateway;

import java.util.Optional;
import com.avaulta.gateway.rules.RecordRules;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
    public static BulkModeConfig fromConfigService(ConfigService configService) {
        BulkModeConfigBuilder builder = BulkModeConfig.builder();
        
        configService.getConfigPropertyAsOptional(BulkModeConfigProperty.BULK_OUTPUT_FORMAT)
            .map(RecordRules.Format::valueOf)
            .ifPresent(builder::outputFormat);
        
        return builder.build();
    }

    /**
     * Output format to use when writing sanitized data.
     * If not present, the input format should be used.
     */
    RecordRules.Format outputFormat;

    public Optional<RecordRules.Format> getOutputFormat() {
        return Optional.ofNullable(outputFormat);
    }

    @AllArgsConstructor
    @lombok.Getter
    enum BulkModeConfigProperty implements ConfigService.ConfigProperty {

        /**
         * Output format to use when writing sanitized data.
         * Values: NDJSON, CSV, PARQUET, JSON_ARRAY
         */
        BULK_OUTPUT_FORMAT;
    }
}
