package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * base test case for rules written in java (although also expected to have yaml-encoded equivalent
 * in repo under `src/main/resources/rules/`)
 */
public abstract class JavaRulesTestBaseCase extends RulesBaseTestCase {


    //TODO: we could avoid this if java rules serialized to YAML as part of build process or
    // something, rather than under version control themselves; or flip so that master copy is the
    // yaml and java ones read from file system??
    @SneakyThrows
    @Test
    void validateYamlExample() {
        String path = "/rules/" + getTestSpec().getYamlSerializationFilePath()
            .orElse(getYamlSerializationFilepath()) + ".yaml";


        RuleSet rulesFromFilesystem = yamlMapper.readerFor(getRulesUnderTest().getClass())
            .readValue(PrebuiltSanitizerRules.class.getResource(path));

        assertEquals(
            yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rulesFromFilesystem),
            yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(getRulesUnderTest()));

    }

}
