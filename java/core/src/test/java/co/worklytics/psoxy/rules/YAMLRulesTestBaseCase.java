package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules;
import lombok.SneakyThrows;

/**
 * base test case for rules defined in YAML
 */
public abstract class YAMLRulesTestBaseCase extends RulesBaseTestCase {


    @SneakyThrows
    @Override
    public Rules2 getRulesUnderTest() {

        return yamlMapper.readerFor(Rules2.class)
            .readValue(PrebuiltSanitizerRules.class.getResource(getRulesTestSpec().getRulesFilePathFull()));
    }
}
