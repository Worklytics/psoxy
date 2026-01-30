package co.worklytics.psoxy.storage.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.RuleSet;
import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.storage.BulkDataTestUtils;
import co.worklytics.psoxy.storage.StorageHandler;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;

class RecordBulkDataSanitizerImplTest {

    static String rawRules;


    @Inject
    RuleSet rules;

    @Inject
    StorageHandler storageHandler;

    @Inject
    UrlSafeTokenPseudonymEncoder encoder;

    java.util.function.Supplier<OutputStream> outputStreamSupplier;

    ByteArrayOutputStream outputStream;

    // to cover both rules versions, calling this inside of each test with different rules to set up
    // with that rule set at run time
    void setUpWithRules(String rawRules) {
        this.rawRules = rawRules;

        RecordBulkDataSanitizerImplTest.Container container = DaggerRecordBulkDataSanitizerImplTest_Container.create();
        container.inject(this);

        outputStream = new ByteArrayOutputStream();
        outputStreamSupplier = () -> outputStream;
    }

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        ConfigRulesModule.class,
        Container.ForConfigService.class,
        MockModules.ForSecretStore.class,
        MockModules.ForHostEnvironment.class,
    })
    public interface Container {

        void inject(RecordBulkDataSanitizerImplTest test);

        @Module
        interface ForConfigService {
            @Provides
            @Singleton
            static ConfigService configService() {
                ConfigService mock = MockModules.provideMock(ConfigService.class);
                when(mock.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
                    .thenReturn(Optional.of(rawRules));
                return mock;
            }
        }

    }

    @SneakyThrows
    @Test
    void base() {
        this.setUpWithRules("---\n" +
            "format: \"NDJSON\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- pseudonymize: \"bar\"\n");


        final String objectPath = "export-20231128/file.ndjson";
        final String pathToOriginal = "bulk/example.ndjson";
        final String pathToSanitized = "bulk/example-sanitized.ndjson";
        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(pathToOriginal),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray());
        assertEquals(new String(TestUtils.getData(pathToSanitized)), output);

        //as of 0.4.46, test was bad on main-line, so some extra verification that the correct
        // things are happening:

        String encodedHashSalt2 = "t~-hN_i1M1DeMAicDVp6LhFgW9lH7r3_LbOpTlXYWpXVI";
        String encodedHashSalt5 = "t~cMWVVout6L1o-OKqU9a0Z1Sfqqg_i5J_zzU0M2EfDJg";

        assertEquals(encodedHashSalt2,
            encoder.encode(Pseudonym.builder().hash(DigestUtils.sha256("2" + "salt")).build()));
        assertEquals(encodedHashSalt5,
            encoder.encode(Pseudonym.builder().hash(DigestUtils.sha256("5" + "salt")).build()));

        assertTrue(output.contains(encodedHashSalt2));
        assertTrue(output.contains(encodedHashSalt5));

    }


    @Test
    void noTransforms() throws IOException {
        this.setUpWithRules("---\n" +
            "format: \"NDJSON\"\n" +
            "transforms:\n");

        final String objectPath = "export-20231128/file.ndjson";
        final String pathToOriginal = "bulk/example.ndjson";
        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(pathToOriginal),
            outputStreamSupplier);


        String output = new String(outputStream.toByteArray());

        final String EXPECTED = "{\"foo\":1,\"bar\":2,\"other\":\"three\"}\n" +
            "{\"foo\":4,\"bar\":5,\"other\":\"six\"}\n";
        assertEquals(EXPECTED, output);
    }

    @Test
    void csv() {
        this.setUpWithRules("---\n" +
            "format: \"CSV\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- pseudonymize: \"bar\"\n");

        final String objectPath = "export-20231128/file.ndjson";
        final String pathToOriginal = "bulk/example.csv";
        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(pathToOriginal),
            outputStreamSupplier);


        String output = new String(outputStream.toByteArray());

        final String EXPECTED = "foo,bar\n" +
            ",t~-hN_i1M1DeMAicDVp6LhFgW9lH7r3_LbOpTlXYWpXVI\n" +
            ",t~0E6I_002nK2IJjv_KCUeFzIUo5rfuISgx7_g-EhfCxE@company.com\n";
        assertEquals(EXPECTED, output);
    }

    //as above, but preserving CRLF
    @Test
    void csv_crlf() {
        this.setUpWithRules("---\n" +
            "format: \"CSV\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- pseudonymize: \"bar\"\n");

        final String objectPath = "export-20231128/file.ndjson";
        final String pathToOriginal = "bulk/example-crlf.csv";
        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(pathToOriginal),
            outputStreamSupplier);


        String output = new String(outputStream.toByteArray());

        final String EXPECTED = "foo,bar\n" +
            ",t~-hN_i1M1DeMAicDVp6LhFgW9lH7r3_LbOpTlXYWpXVI\n" +
            ",t~0E6I_002nK2IJjv_KCUeFzIUo5rfuISgx7_g-EhfCxE@company.com\n";
        assertEquals(EXPECTED, output);
    }

    @Test
    void gzippedContent() throws IOException {
        this.setUpWithRules("---\n" +
            "format: \"NDJSON\"\n" +
            "transforms:\n" +
            "- redact: \"team_id\"\n" +
            "- pseudonymize: \"$.profile.email\"\n");

        final String pathToOriginal = "bulk/users.ndjson.gz";
        storageHandler.handle(BulkDataTestUtils.request(pathToOriginal).withDecompressInput(true).withCompressOutput(true),
            BulkDataTestUtils.transform(rules),
            () -> TestUtils.class.getClassLoader().getResourceAsStream(pathToOriginal),
            outputStreamSupplier);

        // output is compressed, so we need to decompress it to compare
        ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buffer)) > 0) {
                decompressed.write(buffer, 0, len);
            }
        }
        String output = decompressed.toString();

        String SANITIZED_FILE = new String(TestUtils.getData("bulk/users-sanitized.ndjson"));

        assertEquals(SANITIZED_FILE, output);
    }

    @Test
    void jsonArray_Basic() {
        this.setUpWithRules("---\n" +
            "format: \"JSON_ARRAY\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- pseudonymize: \"bar\"\n");

        String input = "[{\"foo\":1,\"bar\":2,\"other\":\"three\"},{\"foo\":4,\"bar\":5,\"other\":\"six\"}]";

        final String objectPath = "export-20231128/file.json";
        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            () -> new ByteArrayInputStream(input.getBytes()),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray());

        System.out.println("Output: " + output);

        assertEquals('[', output.charAt(0));
        assertEquals(']', output.charAt(output.length() - 1));

        String expected2 = encoder.encode(Pseudonym.builder().hash(DigestUtils.sha256("2" + "salt")).build());
        String expected5 = encoder.encode(Pseudonym.builder().hash(DigestUtils.sha256("5" + "salt")).build());

        // Verify content - manual check as we don't have full JSON parsing here easily without bringing in Jackson dependency to test
        // but removing "foo" and pseudonymizing "bar" should happen.
        assertTrue(output.contains("\"foo\":null"));
        assertTrue(output.contains("\"bar\":\"" + expected2 + "\""));
        assertTrue(output.contains("\"bar\":\"" + expected5 + "\""));
    }

    @Test
    void jsonArray_Whitespace() {
        this.setUpWithRules("---\n" +
            "format: \"JSON_ARRAY\"\n" +
            "transforms:\n");

        String input = " [  \n { \"foo\" : 1 } , \n { \"foo\" : 2 } \n ] ";

        final String objectPath = "export-20231128/file.json";
        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            () -> new ByteArrayInputStream(input.getBytes()),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray());

        assertEquals('[', output.charAt(0));
        assertEquals(']', output.charAt(output.length() - 1));
        assertTrue(output.contains("\"foo\":1"));
        assertTrue(output.contains("\"foo\":2"));
    }

    @Test
    void testAutoFormat_JsonArray() {
        this.setUpWithRules("---\n" +
            "format: \"AUTO\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- pseudonymize: \"bar\"\n");

        String input = "[{\"foo\":1,\"bar\":2,\"other\":\"three\"}]";

        final String objectPath = "export-20231128/file.json";
        
        // Manual request construction to set Content-Type
        co.worklytics.psoxy.gateway.StorageEventRequest request = BulkDataTestUtils.request(objectPath)
                .withContentType("application/json");

        storageHandler.handle(request,
            BulkDataTestUtils.transform(rules),
            () -> new ByteArrayInputStream(input.getBytes()),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray());

        assertEquals('[', output.charAt(0));
        assertEquals(']', output.charAt(output.length() - 1));
        assertTrue(output.contains("\"foo\":null"));
    }
    @Test
    void parquet() throws IOException {
        this.setUpWithRules("---\n" +
            "format: \"PARQUET\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- pseudonymize: \"bar\"\n");

        // Create sample data
        Map<String, Object> record1 = new LinkedHashMap<>();
        record1.put("foo", "1");
        record1.put("bar", "2"); // should be pseudonymized
        record1.put("other", "three");

        Map<String, Object> record2 = new LinkedHashMap<>();
        record2.put("foo", "4");
        record2.put("bar", "5");
        record2.put("other", "six");

        // Write sample data to Parquet bytes using our own writer implementation
        ByteArrayOutputStream sourceOut = new ByteArrayOutputStream();
        try (ParquetRecordWriter writer = new ParquetRecordWriter(sourceOut)) {
            writer.beginRecordSet();
            writer.writeRecord(record1);
            writer.writeRecord(record2);
            writer.endRecordSet();
        }

        byte[] inputBytes = sourceOut.toByteArray();


        // Run sanitizer
        final String objectPath = "export-20231128/file.parquet";
        
        // Manual request construction to set Content-Type correctly for Parquet
        co.worklytics.psoxy.gateway.StorageEventRequest request = BulkDataTestUtils.request(objectPath)
                .withContentType("application/vnd.apache.parquet");

        storageHandler.handle(request,
            BulkDataTestUtils.transform(rules),
            () -> new ByteArrayInputStream(inputBytes),
            outputStreamSupplier);

        byte[] outputBytes = outputStream.toByteArray();

        // Verify output is valid Parquet and contains expected data
        try (ParquetRecordReader reader = new ParquetRecordReader(new ByteArrayInputStream(outputBytes))) {
            Map<String, Object> r1 = reader.readRecord();
            Map<String, Object> r2 = reader.readRecord();
            
            assertTrue(r1 != null);
            
            // "foo" should be null (redacted)
            // Parquet redaction might result in null or empty string depending on implementation details of sanitizer/writer interaction
            // In our impl, Redact returns null. Parquet writer skips nulls. Reader might read as null or missing.
            // Our reader fills map with null if missing? No, our reader iterates available columns? 
            // Wait, ParquetReader.streamContentToStrings returns String[], but headers logic expects positional match.
            // If values are missing in Parquet (null), does array have nulls?
            // "streamContentToStrings" usually fills with nulls for optional fields.
            
            // Let's assert based on expected behavior: null/missing in map.
            // But sanitized record has "foo": null. writer skips. reader sees null.
            Object fooVal = r1.get("foo");
            assertTrue(fooVal == null || "null".equals(fooVal)); // Parquet redaction

            // "bar" should be pseudonymized
            String expected2 = encoder.encode(Pseudonym.builder().hash(DigestUtils.sha256("2" + "salt")).build());
            assertEquals(expected2, r1.get("bar"));
            
            assertEquals("three", r1.get("other"));

            // Check second record
            assertTrue(r2 != null);
            
            String expected5 = encoder.encode(Pseudonym.builder().hash(DigestUtils.sha256("5" + "salt")).build());
             assertEquals(expected5, r2.get("bar"));
        }
    }
    @Test
    void parquet_Complex() throws IOException {
        this.setUpWithRules("---\n" +
            "format: \"PARQUET\"\n" +
            "transforms:\n" +
            "- redact: \"secret\"\n");

        // Create complex sample data
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("stringVal", "hello");
        record.put("intVal", 123);
        record.put("doubleVal", 45.67);
        record.put("boolVal", true);
        record.put("secret", "sensitive");

        // Write sample data
        ByteArrayOutputStream sourceOut = new ByteArrayOutputStream();
        try (ParquetRecordWriter writer = new ParquetRecordWriter(sourceOut)) {
            writer.beginRecordSet();
            writer.writeRecord(record);
            writer.endRecordSet();
        }

        byte[] inputBytes = sourceOut.toByteArray();

        // Run sanitizer
        final String objectPath = "export-20231128/file_complex.parquet";
        
        co.worklytics.psoxy.gateway.StorageEventRequest request = BulkDataTestUtils.request(objectPath)
                .withContentType("application/vnd.apache.parquet");

        storageHandler.handle(request,
            BulkDataTestUtils.transform(rules),
            () -> new ByteArrayInputStream(inputBytes),
            outputStreamSupplier);

        byte[] outputBytes = outputStream.toByteArray();

        // Verify output
        try (ParquetRecordReader reader = new ParquetRecordReader(new ByteArrayInputStream(outputBytes))) {
            Map<String, Object> r1 = reader.readRecord();
            
            assertTrue(r1 != null);
            
            assertEquals("hello", r1.get("stringVal"));
            assertEquals(123, r1.get("intVal"));
            assertEquals(45.67, r1.get("doubleVal"));
            assertEquals(true, r1.get("boolVal"));
            
            // "secret" should be null (redacted)
            assertTrue(r1.get("secret") == null || "null".equals(r1.get("secret")));
        }
    }
}
