package com.avaulta.gateway.rules;

import com.avaulta.gateway.rules.transforms.RecordTransform;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class RecordRulesTest {


    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @SneakyThrows
    @Test
    public void fromYaml() {

        InputStream yamlStream = getClass().getClassLoader().getResourceAsStream("rules/record-rules-example.yaml");


        RecordRules rules = yamlMapper.readerFor(RecordRules.class).readValue(yamlStream);

        // Basic checks to ensure deserialization worked (you can add more specific checks)
        assertNotNull(rules);
        assertEquals(RecordRules.Format.valueOf("NDJSON"), rules.getFormat());
        assertNotNull(rules.getTransforms());
        assertFalse(rules.getTransforms().isEmpty());
    }

    @SneakyThrows
    @Test
    public void fromYamlBackwardCompatibility() {
        final String LEGACY_YAML = "---\n" +
            "format: \"NDJSON\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- pseudonymize: \"bar\"\n";

        RecordRules rules = yamlMapper.readValue(LEGACY_YAML, RecordRules.class);
        assertNotNull(rules);
        assertEquals(RecordRules.Format.valueOf("NDJSON"), rules.getFormat());
        assertEquals(2, rules.getTransforms().size());
        
        RecordTransform.Redact redact = (RecordTransform.Redact) rules.getTransforms().get(0);
        assertEquals("foo", redact.getRedact().get(0));

        RecordTransform.Pseudonymize pseudonymize = (RecordTransform.Pseudonymize) rules.getTransforms().get(1);
        assertEquals("bar", pseudonymize.getPseudonymize().get(0));
    }

    @SneakyThrows
    @Test
    public void fromYamlBackwardCompatibilityMultiple() {
        final String LEGACY_YAML = "---\n" +
            "format: \"NDJSON\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- redact: \"biz\"\n" +
            "- pseudonymize: \"bar\"\n" +
            "- pseudonymize: \"baz\"\n";

        RecordRules rules = yamlMapper.readValue(LEGACY_YAML, RecordRules.class);
        assertNotNull(rules);
        assertEquals(RecordRules.Format.valueOf("NDJSON"), rules.getFormat());
        assertEquals(4, rules.getTransforms().size());
        
        RecordTransform.Redact redact1 = (RecordTransform.Redact) rules.getTransforms().get(0);
        assertEquals("foo", redact1.getRedact().get(0));

        RecordTransform.Redact redact2 = (RecordTransform.Redact) rules.getTransforms().get(1);
        assertEquals("biz", redact2.getRedact().get(0));

        RecordTransform.Pseudonymize pseudo1 = (RecordTransform.Pseudonymize) rules.getTransforms().get(2);
        assertEquals("bar", pseudo1.getPseudonymize().get(0));

        RecordTransform.Pseudonymize pseudo2 = (RecordTransform.Pseudonymize) rules.getTransforms().get(3);
        assertEquals("baz", pseudo2.getPseudonymize().get(0));
    }

    @SneakyThrows
    @Test
    public void toYaml() {

        final String EXPECTED = "---\n" +
            "format: \"NDJSON\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- pseudonymize: \"bar\"\n";

        RecordRules recordRules = RecordRules.builder()
            .transform(RecordTransform.Redact.builder()
                .redact("foo")
                .build())
            .transform(RecordTransform.Pseudonymize.builder()
                .pseudonymize("bar")
                .build())
            .build();

        assertEquals(EXPECTED, yamlMapper.writeValueAsString(recordRules));
    }
}
