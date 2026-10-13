package co.worklytics.psoxy.rules;

import com.avaulta.gateway.rules.transforms.Transform;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rules files still in the field set {@code includeReversible}. They must keep parsing
 * through the same {@link YAMLMapper} path as {@link Rules2#load(String)}.
 */
class IncludeReversibleYamlTest {

    @SneakyThrows
    @Test
    @SuppressWarnings("deprecation")
    void rulesYamlWithIncludeReversibleStillParses() {
        String yaml = """
            endpoints:
              - pathTemplate: "/api/1.0/users"
                transforms:
                  - !<pseudonymize>
                    jsonPaths:
                      - "$.data[*].gid"
                    includeReversible: true
                    encoding: "URL_SAFE_TOKEN"
                  - !<pseudonymizeRegexMatches>
                    jsonPaths:
                      - "$.data[*].name"
                    regex: ".*"
                    includeReversible: false
            """;

        Rules2 rules = new YAMLMapper().readerFor(Rules2.class).readValue(yaml);

        Transform.Pseudonymize pseudonymize =
            (Transform.Pseudonymize) rules.getEndpoints().get(0).getTransforms().get(0);
        assertEquals(Boolean.TRUE, pseudonymize.getIncludeReversible());
        assertTrue(pseudonymize.includesEncrypted());

        Transform.PseudonymizeRegexMatches regexMatches =
            (Transform.PseudonymizeRegexMatches) rules.getEndpoints().get(0).getTransforms().get(1);
        assertEquals(Boolean.FALSE, regexMatches.getIncludeReversible());
        assertFalse(regexMatches.includesEncrypted());
    }
}
