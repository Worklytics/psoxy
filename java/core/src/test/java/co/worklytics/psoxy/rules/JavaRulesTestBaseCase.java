package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * base test case for rules written in java (although also expected to have yaml-encoded equivalent
 * in repo under `src/main/resources/rules/`)
 */
public abstract class JavaRulesTestBaseCase extends RulesBaseTestCase {


    @SneakyThrows
    @Test
    void validateYamlExample() {
        String path = "/rules/" + getYamlSerializationFilepath() + ".yaml";

        Rules rulesFromFilesystem = yamlMapper.readerFor(Rules.class)
            .readValue(PrebuiltSanitizerRules.class.getResource(path));

        assertEquals(
            yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rulesFromFilesystem),
            yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(getRulesUnderTest()));
    }

}
