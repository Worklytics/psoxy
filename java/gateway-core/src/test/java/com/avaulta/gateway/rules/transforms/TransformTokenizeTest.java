package com.avaulta.gateway.rules.transforms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransformTokenizeTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @SneakyThrows
    @Test
    public void yaml_omittedIncludeEncrypted_defaultsTrue() {
        Transform.Tokenize parsed = yamlMapper.readValue("""
            --- !<tokenize>
            jsonPaths:
              - "$.nextLink"
            """, Transform.Tokenize.class);

        assertTrue(parsed.includesEncrypted());
        assertNull(parsed.getIncludeEncrypted());
    }

    @SneakyThrows
    @Test
    public void yaml_includeEncryptedFalse() {
        Transform.Tokenize parsed = yamlMapper.readValue("""
            --- !<tokenize>
            jsonPaths:
              - "$.nextLink"
            includeEncrypted: false
            """, Transform.Tokenize.class);

        assertFalse(parsed.includesEncrypted());
        assertFalse(parsed.getIncludeEncrypted());
    }

    @SneakyThrows
    @Test
    @SuppressWarnings("deprecation")
    public void yaml_deprecatedIncludeReversible() {
        Transform.Tokenize parsed = yamlMapper.readValue("""
            --- !<tokenize>
            jsonPaths:
              - "$.nextLink"
            includeReversible: true
            """, Transform.Tokenize.class);

        assertTrue(parsed.includesEncrypted());
        assertTrue(parsed.getIncludeReversible());
    }
}
