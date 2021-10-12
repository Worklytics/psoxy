package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    public abstract String getExampleDirectoryPath();

    protected String asJson(String filePathWithinExampleDirectory) {
        return new String(TestUtils.getData(getExampleDirectoryPath() + "/" + filePathWithinExampleDirectory));
    }

    protected void assertNotSanitized(String content, Collection<String> shouldContain) {
        shouldContain.stream()
            .forEach(s -> assertTrue(content.contains(s), "Unsanitized content does not contain expected string: " + s));
    }

    protected void assertSanitized(String content, Collection<String> shouldNotContain) {
        shouldNotContain.stream()
            .forEach(s -> assertFalse(content.contains(s), "Sanitized content still contains: " + s));
    }

}
