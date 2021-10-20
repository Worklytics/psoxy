package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
abstract public class RulesBaseTestCase {

    protected SanitizerImpl sanitizer;

    @BeforeEach
    public void setup() {
        sanitizer = new SanitizerImpl(Sanitizer.Options.builder()
            .rules(getRulesUnderTest())
            .defaultScopeId(getDefaultScopeId())
            .build());
    }

    @Test
    void validate() {
        Validator.validate(getRulesUnderTest());
    }


    public abstract String getDefaultScopeId();

    public abstract Rules getRulesUnderTest();

    public abstract String getExampleDirectoryPath();

    protected String asJson(String filePathWithinExampleDirectory) {
        return new String(TestUtils.getData(getExampleDirectoryPath() + "/" + filePathWithinExampleDirectory));
    }

    protected void assertNotSanitized(String content, Collection<String> shouldContain) {
        shouldContain.stream()
            .forEach(s -> assertTrue(content.contains(s), "Unsanitized content does not contain expected string: " + s));
    }

    @Deprecated //used pseudonymized or redacted
    protected void assertSanitized(String content, Collection<String> shouldNotContain) {
        shouldNotContain.stream()
            .forEach(s -> assertFalse(content.contains(s), "Sanitized content still contains: " + s));
    }

    protected void assertRedacted(String content, Collection<String> shouldNotContain) {
        shouldNotContain.stream()
            .forEach(s -> assertFalse(content.contains(s), "Sanitized content still contains: " + s));

        shouldNotContain.stream()
            .forEach(s -> {
                assertFalse(content.contains(sanitizer.pseudonymizeToJson(s, sanitizer.getJsonConfiguration())),
                    "Sanitized contains pseudonymized equivalent of: " + s);
            });
    }
    protected void assertRedacted(String content, String... shouldNotContain) {
        assertRedacted(content, Arrays.asList(shouldNotContain));
    }


    protected void assertPseudonymized(String content, Collection<String> shouldBePseudonymized) {
        shouldBePseudonymized.stream()
            .forEach(s -> assertFalse(content.contains(s), "Sanitized content still contains unpseudonymized: " + s));

        shouldBePseudonymized.stream()
            .forEach(s -> {
                String doubleJsonEncodedPseudonym =
                    sanitizer.getJsonConfiguration().jsonProvider().toJson(sanitizer.pseudonymizeToJson(s, sanitizer.getJsonConfiguration()));
                assertTrue(content.contains(doubleJsonEncodedPseudonym),
                    String.format("Sanitized does not contain %s, pseudonymized equivalent of %s", doubleJsonEncodedPseudonym, s));
            });
    }

    protected void assertPseudonymized(String content, String... shouldBePseudonymized) {
        assertPseudonymized(content, Arrays.asList(shouldBePseudonymized));
    }

}
