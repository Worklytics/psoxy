package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * abstract test stuff for Rules implementations
 *
 * re-use through inheritance, so rather inflexible
 * q: better as junit Extension or something? how do to that
 *
 */
abstract public class RulesTest {

    protected SanitizerImpl sanitizer;

    @BeforeEach
    public void setup() {
        sanitizer = new SanitizerImpl(Sanitizer.Options.builder()
            .rules(getRulesUnderTest())
            .build());
    }

    @Test
    void validate() {
        Validator.validate(getRulesUnderTest());
    }

    public abstract Rules getRulesUnderTest();


}
