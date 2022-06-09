package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules;
import lombok.SneakyThrows;

/**
 * base test case for rules defined in YAML
 */
public abstract class YAMLRulesTestBaseCase extends RulesBaseTestCase {


    @SneakyThrows
    @Override
    public Rules1 getRulesUnderTest() {
        String path = "/rules/" + getYamlSerializationFilepath() + ".yaml";

        return yamlMapper.readerFor(Rules1.class)
            .readValue(PrebuiltSanitizerRules.class.getResource(path));
    }
}
