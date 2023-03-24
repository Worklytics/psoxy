package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.BulkModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.test.MockModules;
import dagger.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.InputStreamReader;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageHandlerTest {

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        MockModules.ForRules.class,
        MockModules.ForConfigService.class
    })
    public interface Container {
        void inject( StorageHandlerTest test);
    }

    @Inject
    ConfigService config;

    @Inject
    StorageHandler handler;

    @BeforeEach
    public void setup() {
        Container container = DaggerStorageHandlerTest_Container.create();
        container.inject(this);

        when(config.getConfigPropertyOrError(eq(BulkModeConfigProperty.OUTPUT_BUCKET)))
            .thenReturn("bucket");
    }

    @ValueSource(strings = { "", "/", "/foo", "/foo/bar" })
    @ParameterizedTest
    void buildDefaultTransform(String outputBasePath) {
        when(config.getConfigPropertyAsOptional(eq(BulkModeConfigProperty.OUTPUT_BASE_PATH)))
            .thenReturn(Optional.of(outputBasePath));


        assertEquals(outputBasePath,
            handler.buildDefaultTransform().getPathWithinBucket());
    }

    @Test
    void buildRequest() {
        when(config.getConfigPropertyAsOptional(eq(BulkModeConfigProperty.INPUT_BASE_PATH)))
            .thenReturn(Optional.empty());
        when(config.getConfigPropertyAsOptional(eq(BulkModeConfigProperty.OUTPUT_BASE_PATH)))
            .thenReturn(Optional.empty());

        StorageEventRequest request = handler.buildRequest(mock(InputStreamReader.class), "bucket-in", "directory/file.csv", handler.buildDefaultTransform());

        assertEquals("directory/file.csv", request.getDestinationObjectPath());
    }


    /**
     * test a bunch of permutations of how this could be configured
     */
    @CsvSource({
        ",,directory/file.csv",
        "directory/,,file.csv",
        ",out/,out/directory/file.csv",
        "directory/,out/,out/file.csv",
        "in/,out/,out/directory/file.csv", //yeah, weird case
    })
    @ParameterizedTest
    void buildRequest(String inputBasePath, String outputBasePath, String expectedOutputPath) {
        when(config.getConfigPropertyAsOptional(eq(BulkModeConfigProperty.INPUT_BASE_PATH)))
            .thenReturn(Optional.ofNullable(inputBasePath));
        when(config.getConfigPropertyAsOptional(eq(BulkModeConfigProperty.OUTPUT_BASE_PATH)))
            .thenReturn(Optional.ofNullable(outputBasePath));

        StorageEventRequest request = handler.buildRequest(mock(InputStreamReader.class), "bucket-in", "directory/file.csv", handler.buildDefaultTransform());

        assertEquals("bucket-in", request.getSourceBucketName());
        assertEquals("directory/file.csv", request.getSourceObjectPath());
        assertEquals("bucket", request.getDestinationBucketName());
        assertEquals(expectedOutputPath, request.getDestinationObjectPath());
    }
}
