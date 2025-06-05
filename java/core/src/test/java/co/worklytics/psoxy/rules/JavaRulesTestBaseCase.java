package co.worklytics.psoxy.rules;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;

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
        String path = getRulesTestSpec().getRulesFilePathFull();

        URL url = this.getClass().getClassLoader().getResource(path);

        com.avaulta.gateway.rules.RuleSet rulesFromFilesystem = yamlMapper.readerFor(getRulesUnderTest().getClass())
            .readValue(url);

        //NOTE: this is testing equivalence of file-system rules after round-trip; not necessarily
        // that current file-system rules are byte-wise equivalent
        assertEquals(
            yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rulesFromFilesystem),
            yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(getRulesUnderTest())
        );
    }

}
