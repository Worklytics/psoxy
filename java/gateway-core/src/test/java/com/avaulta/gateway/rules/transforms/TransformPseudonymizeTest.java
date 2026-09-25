package com.avaulta.gateway.rules.transforms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransformPseudonymizeTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @SneakyThrows
    @Test
    public void yaml_includeEncrypted() {
        Transform.Pseudonymize parsed = yamlMapper.readValue("""
            --- !<pseudonymize>
            jsonPaths:
              - "$.id"
            includeEncrypted: true
            """, Transform.Pseudonymize.class);

        assertTrue(parsed.includesEncrypted());
        assertNull(parsed.getIncludeReversible());
        assertTrue(parsed.encryptionFlagsConflict().isEmpty());
    }

    @SneakyThrows
    @Test
    @SuppressWarnings("deprecation")
    public void yaml_deprecatedIncludeReversible() {
        Transform.Pseudonymize parsed = yamlMapper.readValue("""
            --- !<pseudonymize>
            jsonPaths:
              - "$.id"
            includeReversible: true
            """, Transform.Pseudonymize.class);

        assertTrue(parsed.includesEncrypted());
        assertTrue(parsed.getIncludeReversible());
        assertNull(parsed.getIncludeEncrypted());
        assertTrue(parsed.encryptionFlagsConflict().isEmpty());
    }

    @SneakyThrows
    @Test
    @SuppressWarnings("deprecation")
    public void yaml_explicitIncludeReversibleFalseIsRespected() {
        Transform.Pseudonymize parsed = yamlMapper.readValue("""
            --- !<pseudonymize>
            jsonPaths:
              - "$.id"
            includeReversible: false
            """, Transform.Pseudonymize.class);

        assertFalse(parsed.getIncludeReversible());
        assertNull(parsed.getIncludeEncrypted());
        assertFalse(parsed.includesEncrypted());
        assertTrue(parsed.encryptionFlagsConflict().isEmpty());
    }

    @SneakyThrows
    @Test
    @SuppressWarnings("deprecation")
    public void yaml_bothFlagsSet_warnsAndPrefersIncludeEncrypted() {
        Transform.Pseudonymize parsed = yamlMapper.readValue("""
            --- !<pseudonymize>
            jsonPaths:
              - "$.id"
            includeReversible: false
            includeEncrypted: true
            """, Transform.Pseudonymize.class);

        assertTrue(parsed.includesEncrypted());
        assertTrue(parsed.encryptionFlagsConflict().isPresent());

        Transform.PseudonymizeRegexMatches regex = yamlMapper.readValue("""
            --- !<pseudonymizeRegexMatches>
            regex: ".*"
            includeReversible: true
            includeEncrypted: false
            """, Transform.PseudonymizeRegexMatches.class);

        assertFalse(regex.includesEncrypted());
        assertTrue(regex.encryptionFlagsConflict().isPresent());
    }
}
