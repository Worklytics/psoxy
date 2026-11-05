package co.worklytics.psoxy.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import co.worklytics.psoxy.gateway.BulkModeConfig.BulkModeConfigProperty;

class BulkModeConfigTest {

    @Test
    void enablesParallelProcessingWhenThreadCountSet() {
        ConfigService configService = mockWithOutputBucket();
        when(configService.getConfigPropertyAsOptional(BulkModeConfigProperty.PARALLEL_PROCESSING_THREADS))
            .thenReturn(Optional.of("8"));

        BulkModeConfig config = BulkModeConfig.fromConfigService(
            configService, new ObjectMapper(new YAMLFactory()));

        assertEquals(Optional.of(8), config.getParallelProcessingThreads());
    }

    @Test
    void acceptsDeprecatedParallelRecordProcessingThreadsEnvVar() {
        ConfigService configService = mockWithOutputBucket();
        when(configService.getConfigPropertyAsOptional(BulkModeConfigProperty.PARALLEL_PROCESSING_THREADS))
            .thenReturn(Optional.empty());
        when(configService.getConfigPropertyAsOptional(BulkModeConfigProperty.PARALLEL_RECORD_PROCESSING_THREADS))
            .thenReturn(Optional.of("6"));

        BulkModeConfig config = BulkModeConfig.fromConfigService(
            configService, new ObjectMapper(new YAMLFactory()));

        assertEquals(Optional.of(6), config.getParallelProcessingThreads());
    }

    @Test
    void legacyParallelFlagUsesDefaultThreadCount() {
        ConfigService configService = mockWithOutputBucket();
        when(configService.getConfigPropertyAsOptional(BulkModeConfigProperty.PARALLEL_RECORD_PROCESSING))
            .thenReturn(Optional.of("true"));

        BulkModeConfig config = BulkModeConfig.fromConfigService(
            configService, new ObjectMapper(new YAMLFactory()));

        assertEquals(Optional.of(BulkModeConfig.DEFAULT_PARALLEL_PROCESSING_THREADS),
            config.getParallelProcessingThreads());
    }

    @Test
    void parallelProcessingDefaultsToDisabled() {
        ConfigService configService = mockWithOutputBucket();

        BulkModeConfig config = BulkModeConfig.fromConfigService(
            configService, new ObjectMapper(new YAMLFactory()));

        assertTrue(config.getParallelProcessingThreads().isEmpty());
    }

    @Test
    void rejectsNonPositiveThreadCount() {
        ConfigService configService = mockWithOutputBucket();
        when(configService.getConfigPropertyAsOptional(BulkModeConfigProperty.PARALLEL_PROCESSING_THREADS))
            .thenReturn(Optional.of("0"));

        assertThrows(IllegalArgumentException.class, () ->
            BulkModeConfig.fromConfigService(configService, new ObjectMapper(new YAMLFactory())));
    }

    @Test
    void rejectsExcessiveThreadCount() {
        ConfigService configService = mockWithOutputBucket();
        when(configService.getConfigPropertyAsOptional(BulkModeConfigProperty.PARALLEL_PROCESSING_THREADS))
            .thenReturn(Optional.of("1000"));

        assertThrows(IllegalArgumentException.class, () ->
            BulkModeConfig.fromConfigService(configService, new ObjectMapper(new YAMLFactory())));
    }

    private static ConfigService mockWithOutputBucket() {
        ConfigService configService = Mockito.mock(ConfigService.class);
        when(configService.getConfigPropertyOrError(BulkModeConfigProperty.OUTPUT_BUCKET))
            .thenReturn("output-bucket");
        return configService;
    }
}
