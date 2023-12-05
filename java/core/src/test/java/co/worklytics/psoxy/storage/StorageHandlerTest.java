package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.rules.*;
import com.google.common.collect.ImmutableMap;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockMakers;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StorageHandlerTest {

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        ForRules.class,
        MockModules.ForConfigService.class,
        MockModules.ForHostEnvironment.class,
    })
    public interface Container {
        void inject( StorageHandlerTest test);
    }

    @Module
    public interface ForRules {
        @Provides
        @Singleton
        static BulkDataRules bulkRules() {
            return ColumnarRules.builder()
                .columnsToPseudonymize(Arrays.asList("foo"))
                .build();
        }

        @Provides
        @Singleton
        static RuleSet rules(BulkDataRules rules) {
            return rules;
        }

    }

    InputStreamReader mockReader;

    Writer writer;
    ByteArrayOutputStream outputStream;

    @Inject
    ConfigService config;

    // as provider to be able to set up config mock first
    @Inject
    Provider<StorageHandler> handlerProvider;

    @Inject
    BulkDataRules rules;

    // actual class under test
    StorageHandler handler;



    @BeforeEach
    public void setup() {
        Container container = DaggerStorageHandlerTest_Container.create();
        container.inject(this);

        when(config.getConfigPropertyOrError(eq(BulkModeConfigProperty.OUTPUT_BUCKET)))
            .thenReturn("bucket");

        when(config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.SOURCE)))
            .thenReturn(Optional.of("hris"));

        handler = handlerProvider.get();

        mockReader = MockModules.provideMock(InputStreamReader.class);

        outputStream = new ByteArrayOutputStream();
        writer = new BufferedWriter(new OutputStreamWriter(outputStream));

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



        StorageEventRequest request = handler.buildRequest(mockReader, writer, "bucket-in", "directory/file.csv", handler.buildDefaultTransform());

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

        StorageHandler.ObjectTransform tranform = StorageHandler.ObjectTransform.builder()
            .destinationBucketName(config.getConfigPropertyOrError(BulkModeConfigProperty.OUTPUT_BUCKET))
            .pathWithinBucket(config.getConfigPropertyAsOptional(BulkModeConfigProperty.OUTPUT_BASE_PATH).orElse(""))
            .rules(mock(BulkDataRules.class))
            .build();

        StorageEventRequest request = handler.buildRequest(mockReader, writer, "bucket-in", "directory/file.csv", tranform);

        assertEquals("bucket-in", request.getSourceBucketName());
        assertEquals("directory/file.csv", request.getSourceObjectPath());
        assertEquals("bucket", request.getDestinationBucketName());
        assertEquals(expectedOutputPath, request.getDestinationObjectPath());
    }

    @Test
    public void getObjectMetadata() {

        //kinda pointless

        assertTrue(handler.buildObjectMetadata("bucket", "/directory/file.csv", handler.buildDefaultTransform())
            .containsKey(StorageHandler.BulkMetaData.INSTANCE_ID.getMetaDataKey()));

    }

    @Test
    public void hasBeenSanitized() {
        assertFalse(handler.hasBeenSanitized(null));
        assertFalse(handler.hasBeenSanitized(ImmutableMap.of()));
        assertTrue(handler.hasBeenSanitized(ImmutableMap.of(StorageHandler.BulkMetaData.INSTANCE_ID.getMetaDataKey(), "psoxy-test")));
    }

    @CsvSource({
        "/directory/file.csv,false", // path has a suffix parameter, so must have one
        "/directory/file1.csv,true",
        "directory/file.csv,false",
        "directory/file1.csv,false",
        "/directory/plain.csv,true",
    })
    @ParameterizedTest
    public void applicableRules_multi(String path, boolean match) {
        MultiTypeBulkDataRules multiTypeBulkDataRules = MultiTypeBulkDataRules.builder()
            .fileRules(ImmutableMap.of(
                "/directory/plain.csv",
                ColumnarRules.builder()
                    .columnsToPseudonymize(Arrays.asList("foo"))
                    .build(),
                "/directory/file{suffix}.csv",
                ColumnarRules.builder()
                    .columnsToPseudonymize(Arrays.asList("foo"))
                    .build()
            )).build();

        assertEquals(match, handler.getApplicableRules(multiTypeBulkDataRules, path).isPresent());
    }

    @ValueSource(
        booleans = {
            true,
            false
        }
    )
    @SneakyThrows
    @ParameterizedTest
    public void process_compressed(boolean compress) {
        String data = "foo,bar\r\n1,2\r\n1,2\n1,2\n";
        String expected = "foo,bar\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",2\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",2\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",2\r\n";

        InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(data.getBytes()));

        StorageEventRequest request = StorageEventRequest.builder()
            .sourceBucketName("bucket")
            .sourceObjectPath("directory/file.csv")
            .sourceReader(reader)
            .destinationBucketName("bucket")
            .destinationObjectPath("directory/file.csv")
            .destinationWriter(writer)
            .compressOutput(compress)
            .build();

        handler.process(request, handler.buildDefaultTransform(), reader, outputStream);
        writer.close();

        String output = compress ? Base64.getEncoder().encodeToString(outputStream.toByteArray()) : new String(outputStream.toByteArray());
        String encodedExpected = compress ? base64gziped(expected) : expected;
        assertEquals(encodedExpected, output);

        assertEquals(compress,  outputStream.toByteArray().length < expected.length());

    }

    String base64gziped(String content) {
        if (content == null || content.length() == 0) {
            return "";
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos)) {

            gzipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
            gzipOutputStream.close();

            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
