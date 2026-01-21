package co.worklytics.psoxy.storage.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.RecordRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

class RecordBulkDataSanitizerJsonTest {

    RecordBulkDataSanitizerImpl sanitizer;
    
    ObjectMapper objectMapper = new ObjectMapper();
    UrlSafeTokenPseudonymEncoder encoder = new UrlSafeTokenPseudonymEncoder();
    Configuration jsonConfiguration;

    ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        jsonConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider(objectMapper))
            .mappingProvider(new JacksonMappingProvider(objectMapper))
            .build();
            
        outputStream = new ByteArrayOutputStream();
    }
    
    // Manual setup helper
    void setupSanitizer(String yamlRules) {
        Yaml yaml = new Yaml();
        Map<String, Object> rulesMap = yaml.load(yamlRules);
        
        // Manual mapping of YAML to RecordRules since we don't have the full Dagger/Config stack working easily here
        // Actually, ObjectMapper can parse YAML to RecordRules if we have the module
        // But let's build RecordRules manually for simplicity/robustness against classpath issues?
        // No, RecordRules is a complex object.
        // Let's rely on Jackson YAML support if available.
        // core pom has `jackson-dataformat-yaml`.
        
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
            RecordRules rules = yamlMapper.readValue(yamlRules, RecordRules.class);
            
            sanitizer = new RecordBulkDataSanitizerImpl(rules);
            
            // Inject fields manually
            // Use reflection or just package-private access if available?
            // Fields are package-private (default) or public?
            // @Inject fields in RecordBulkDataSanitizerImpl are package-private/default.
            
            sanitizer.jsonConfiguration = jsonConfiguration;
            sanitizer.encoder = encoder;
            sanitizer.objectMapper = objectMapper;
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void jsonArray_Basic() throws Exception {
        setupSanitizer("---\n" +
            "format: \"JSON_ARRAY\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- pseudonymize: \"bar\"\n");

        String input = "[{\"foo\":1,\"bar\":2,\"other\":\"three\"},{\"foo\":4,\"bar\":5,\"other\":\"six\"}]";
        
        java.io.Reader reader = new java.io.InputStreamReader(new java.io.ByteArrayInputStream(input.getBytes()));
        java.io.Writer writer = new java.io.OutputStreamWriter(outputStream);
        
        // We need to bypass StorageHandler and call sanitizer directly since StorageHandler uses Dagger heavily too
        // and we want to isolate RecordBulkDataSanitizerImpl logic.
        // RecordBulkDataSanitizerImpl.sanitize(Reader, Writer, Pseudonymizer)
        
        // Mock Pseudonymizer
        co.worklytics.psoxy.Pseudonymizer pseudonymizer = mock(co.worklytics.psoxy.Pseudonymizer.class);
        // Mock pseudonymize behavior
        when(pseudonymizer.pseudonymize(any())).thenAnswer(invocation -> {
             Object arg = invocation.getArgument(0);
             // Generate a valid encoded hash for the argument using the encoder
             // We need a byte array for hash.
             String encoded = encoder.encode(com.avaulta.gateway.pseudonyms.Pseudonym.builder()
                 .hash(arg.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                 .build());
             
             return co.worklytics.psoxy.PseudonymizedIdentity.builder()
                 .hash(encoded)
                 .original(arg.toString())
                 .build();
        });
        
        sanitizer.sanitize(reader, writer, pseudonymizer);
        writer.flush(); // outputStreamWriter needs flush
        
        String output = new String(outputStream.toByteArray());
        
        System.out.println("Output: " + output);
        
        assertEquals('[', output.charAt(0));
        assertEquals(']', output.charAt(output.length() - 1));
        
        // Check content
        // foo should be redacted (null)
        // bar should be pseudonymized ("2_hashed", "5_hashed")
        
        // Parse output to verify structure
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(output);
        
        assertEquals(2, root.size());
        
        String expected2 = encoder.encode(com.avaulta.gateway.pseudonyms.Pseudonym.builder()
            .hash("2".getBytes(java.nio.charset.StandardCharsets.UTF_8)).build());
        String expected5 = encoder.encode(com.avaulta.gateway.pseudonyms.Pseudonym.builder()
            .hash("5".getBytes(java.nio.charset.StandardCharsets.UTF_8)).build());
        
        assertEquals(true, root.get(0).get("foo").isNull());
        assertEquals(expected2, root.get(0).get("bar").asText());
        assertEquals("three", root.get(0).get("other").asText());
        
        assertEquals(true, root.get(1).get("foo").isNull());
        assertEquals(expected5, root.get(1).get("bar").asText());
    }

    @Test
    void jsonArray_Whitespace() throws Exception {
        setupSanitizer("---\n" +
            "format: \"JSON_ARRAY\"\n" +
            "transforms:\n");

        String input = " [  \n { \"foo\" : 1 } , \n { \"foo\" : 2 } \n ] ";

        java.io.Reader reader = new java.io.InputStreamReader(new java.io.ByteArrayInputStream(input.getBytes()));
        java.io.Writer writer = new java.io.OutputStreamWriter(outputStream);
        
        co.worklytics.psoxy.Pseudonymizer pseudonymizer = mock(co.worklytics.psoxy.Pseudonymizer.class);
        
        sanitizer.sanitize(reader, writer, pseudonymizer);
        writer.flush();

        String output = new String(outputStream.toByteArray());
        
        // Output should be compact JSON array
        // Order of keys might vary in JSON object, but with 1 key it's fine.
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(output);
        assertEquals(2, root.size());
        assertEquals(1, root.get(0).get("foo").asInt());
        assertEquals(2, root.get(1).get("foo").asInt());
    }
}
