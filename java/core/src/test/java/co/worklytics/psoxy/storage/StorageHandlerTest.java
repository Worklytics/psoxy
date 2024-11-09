package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.test.MockModules;
import com.avaulta.gateway.rules.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Named;
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


    @Named("ForYAML")
    @Inject
    ObjectMapper yamlMapper;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        ForRules.class,
        MockModules.ForConfigService.class,
        MockModules.ForHostEnvironment.class,
        MockModules.ForSecretStore.class,
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



        StorageEventRequest request =
            handler.buildRequest("bucket-in", "directory/file.csv", handler.buildDefaultTransform(), null);

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

        StorageHandler.ObjectTransform transform = StorageHandler.ObjectTransform.builder()
            .destinationBucketName(config.getConfigPropertyOrError(BulkModeConfigProperty.OUTPUT_BUCKET))
            .pathWithinBucket(config.getConfigPropertyAsOptional(BulkModeConfigProperty.OUTPUT_BASE_PATH).orElse(""))
            .rules(mock(BulkDataRules.class))
            .build();

        StorageEventRequest request = handler.buildRequest("bucket-in", "directory/file.csv", transform, null);

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
        "directory/file.csv,false", // path has a suffix parameter, so must have one
        "directory/file1.csv,true",
        "directory/plain.csv,true",
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
    public void process_compressOutput(boolean compress) {
        String data = "foo,bar\r\n1,2\r\n1,2\n1,2\n";
        String expected = "foo,bar\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",2\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",2\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",2\r\n";

        InputStream is = new ByteArrayInputStream(data.getBytes());

        StorageEventRequest request = StorageEventRequest.builder()
            .sourceBucketName("bucket")
            .sourceObjectPath("directory/file.csv")
            .destinationBucketName("bucket")
            .destinationObjectPath("directory/file.csv")
            .compressOutput(compress)
            .build();

        handler.process(request, handler.buildDefaultTransform(), () -> is, () -> outputStream);
        writer.close();

        String output = compress ? Base64.getEncoder().encodeToString(outputStream.toByteArray()) : new String(outputStream.toByteArray());
        String encodedExpected = compress ? base64gzipped(expected) : expected;
        assertEquals(encodedExpected, output);

        assertEquals(compress,  outputStream.toByteArray().length < expected.length());
    }

    @SneakyThrows
    @Test
    public void process_compressInput() {
        String data = "foo,bar\r\n1,2\r\n1,2\n1,2\n";
        String expected = "foo,bar\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",2\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",2\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",2\r\n";

        InputStream is = new ByteArrayInputStream(compress(data.getBytes(StandardCharsets.UTF_8)));

        StorageEventRequest request = StorageEventRequest.builder()
            .sourceBucketName("bucket")
            .sourceObjectPath("directory/file.csv")
            .destinationBucketName("bucket")
            .destinationObjectPath("directory/file.csv")
            .decompressInput(true)
            .build();

        handler.process(request, handler.buildDefaultTransform(), () -> is, () -> outputStream);
        writer.close();

        String output = new String(outputStream.toByteArray());
        assertEquals(expected, output);
    }

    @SneakyThrows
    @Test
    public void applicableRules_multipleMatches() {

        //ambiguous rules
        String yaml = "fileRules:\n" +
            "  /directory/{fileName}.csv:\n" +
            "    columnsToPseudonymize:\n" +
            "      - foo\n" +
            "  /directory/file.{extension}:\n" +
            "    columnsToPseudonymize:\n" +
            "      - bar\n";

        MultiTypeBulkDataRules multiTypeBulkDataRules = yamlMapper.readValue(yaml, MultiTypeBulkDataRules.class);



        // this case can match either, so expect to match first
        assertEquals("foo",
            ((ColumnarRules) handler.getApplicableRules(multiTypeBulkDataRules, "directory/file.csv").get()).getColumnsToPseudonymize().get(0));

        // this case can can only match the second
        assertEquals("bar",
            ((ColumnarRules) handler.getApplicableRules(multiTypeBulkDataRules, "directory/file.ndjson").get()).getColumnsToPseudonymize().get(0));

        String reversed = "fileRules:\n" +
            "  /directory/file.{extension}:\n" +
            "    columnsToPseudonymize:\n" +
            "      - bar\n" +
            "  /directory/{fileName}.csv:\n" +
            "    columnsToPseudonymize:\n" +
            "      - foo\n";

        MultiTypeBulkDataRules reversedRules = yamlMapper.readValue(reversed, MultiTypeBulkDataRules.class);

        assertEquals("bar",
            ((ColumnarRules) handler.getApplicableRules(reversedRules, "directory/file.csv").get()).getColumnsToPseudonymize().get(0));

        assertEquals("bar",
            ((ColumnarRules) handler.getApplicableRules(reversedRules, "directory/file.ndjson").get()).getColumnsToPseudonymize().get(0));
    }

    @ParameterizedTest
    @CsvSource({
        "directory/file.csv,gzip,true",         // honor content-encoding
        "directory/file.csv.gz,gzip,true",      // honor content-encoding/extension
        "directory/file.json.gz,deflate,true",  // honor extension
        "directory/file.json.gz,,true",         // honor extension - null encoding
        "directory/file.json,deflate,false",    // deflate is not supported
        "directory/file.json,,false"
    })
    public void handlesCompressedContent_legacy(String filename, String contentEncoding, boolean expected) {

        // configure such that legacy behavior
        when(config.getConfigPropertyAsOptional(BulkModeConfigProperty.COMPRESS_OUTPUT_ALWAYS))
            .thenReturn(Optional.of("false"));

        StorageEventRequest request = handler.buildRequest("bucket", filename, handler.buildDefaultTransform(), contentEncoding);
        assertEquals(expected, request.getDecompressInput());
        assertEquals(expected, request.getCompressOutput());
    }

    @ParameterizedTest
    @CsvSource({
        "directory/file.csv,gzip,true,true",     // honor content-encoding
        "directory/file.csv.gz,gzip,true,true",  // honor content-encoding/extension
        "directory/file.json.gz,,true,true",     // honor extension
        "directory/file.json,,false,true"        // compress even though input was not compressed
    })
    public void handlesCompressedContent(String filename, String contentEncoding, boolean expectCompressedInput, boolean expectCompressedOutput) {

        // configure such that legacy behavior
        when(config.getConfigPropertyAsOptional(BulkModeConfigProperty.COMPRESS_OUTPUT_ALWAYS))
            .thenReturn(Optional.of("true"));

        StorageEventRequest request = handler.buildRequest("bucket", filename, handler.buildDefaultTransform(), contentEncoding);
        assertEquals(expectCompressedInput, request.getDecompressInput());
        assertEquals(expectCompressedOutput, request.getCompressOutput());
    }

    @ParameterizedTest
    @CsvSource({
        //inputCompressed,configSetting,transformSetting,expected
        // explicit transform setting should prevail
        "true,true,true,true",
        "true,true,false,false",
        "true,false,true,true",
        "false,true,true,true",
        "false,false,false,false",
        "false,,true,true",
        "false,,false,false",

        // nothing for config/transform should default to 'true'
        "false,,,true",
        "true,,,true",

        // when config setting is false, should match input
        "true,false,,true",
        "false,false,,false",

        // when config setting is true and no transform setting, should be true
        "true,true,,true",
        "false,true,,true",
    })
    public void compressOutputSetting(Boolean inputCompressed, Boolean configSetting, Boolean transformSetting, Boolean expected) {
        when(config.getConfigPropertyAsOptional(BulkModeConfigProperty.COMPRESS_OUTPUT_ALWAYS))
            .thenReturn(configSetting == null ? Optional.empty() : Optional.of(configSetting.toString()));

        StorageHandler.ObjectTransform transform = StorageHandler.ObjectTransform.builder()
            .destinationBucketName(config.getConfigPropertyOrError(BulkModeConfigProperty.OUTPUT_BUCKET))
            .pathWithinBucket(config.getConfigPropertyAsOptional(BulkModeConfigProperty.OUTPUT_BASE_PATH).orElse(""))
            .rules(mock(BulkDataRules.class))
            .compressOutput(transformSetting)
            .build();

        assertEquals(expected, handler.compressOutput(inputCompressed, config,  transform));
    }

    @SneakyThrows
    byte[] compress(byte[] content) {
        if (content == null || content.length == 0) {
            return new byte[0];
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos)) {

            gzipOutputStream.write(content);
            gzipOutputStream.close();

            return baos.toByteArray();
        }

    }

    String base64gzipped(String content) {
        if (content == null || content.length() == 0) {
            return "";
        }

        return Base64.getEncoder().encodeToString(compress(content.getBytes(StandardCharsets.UTF_8)));
    }
}
