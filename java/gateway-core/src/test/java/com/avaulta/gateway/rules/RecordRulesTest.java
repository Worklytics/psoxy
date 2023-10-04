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
    public void toYaml() {

        final String EXPECTED = "---\n" +
            "format: \"NDJSON\"\n" +
            "transforms:\n" +
            "- !<redact>\n" +
            "  path: \"foo\"\n" +
            "- !<pseudonymize>\n" +
            "  path: \"bar\"\n";

        RecordRules recordRules = RecordRules.builder()
            .transform(RecordTransform.Redact.builder()
                .path("foo")
                .build())
            .transform(RecordTransform.Pseudonymize.builder()
                .path("bar")
                .build())
            .build();

        assertEquals(EXPECTED, yamlMapper.writeValueAsString(recordRules));
    }
}
