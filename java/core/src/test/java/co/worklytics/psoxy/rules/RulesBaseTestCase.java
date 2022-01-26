package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.SanitizerFactory;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Component;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * abstract test stuff for Rules implementations
 *
 * re-use through inheritance, so rather inflexible
 * q: better as junit Extension or something? how do to that
 *
 */
abstract public class RulesBaseTestCase {

    protected SanitizerImpl sanitizer;

    @Inject
    protected ObjectMapper jsonMapper;
    @Inject @Named("ForYAML")
    protected ObjectMapper yamlMapper;
    @Inject
    protected SanitizerFactory sanitizerFactory;



    //q: how to avoid boilerplate of this at top of every test?
    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        MockModules.ForConfigService.class,
    })
    public interface Container {
        void inject(RulesBaseTestCase test);
    }

    @BeforeEach
    public void setup() {
        //q: how to avoid boilerplate of this at top of every test?
        // idea: use reflection to get test-specific container class, and call create() on that??
        //  eg., 'DaggerRulesBaseTestCase_Container' class,

        Container container = DaggerRulesBaseTestCase_Container.create();
        container.inject(this);

        sanitizer = sanitizerFactory.create(Sanitizer.Options.builder()
            .rules(getRulesUnderTest())
            .defaultScopeId(getDefaultScopeId())
            .build());

        //q: good way to also batch test sanitizers from yaml/json formats of rules, to ensure
        // serialization doesn't materially change any behavior??
    }

    @Test
    void validate() {
        Validator.validate(getRulesUnderTest());
    }

    @SneakyThrows
    @Test
    void validateYaml() {
        Validator.validate(yamlRoundtrip(getRulesUnderTest()));
    }

    @SneakyThrows
    @Test
    void validateJSON() {
        Validator.validate(jsonRoundtrip(getRulesUnderTest()));
    }



    @SneakyThrows
    Rules yamlRoundtrip(Rules rules) {
        String yaml = yamlMapper.writeValueAsString(getRulesUnderTest()).replace("---\n", "");
        return yamlMapper.readerFor(Rules.class).readValue(yaml);
    }

    @SneakyThrows
    Rules jsonRoundtrip(Rules rules) {
        String json = jsonMapper.writeValueAsString(getRulesUnderTest());
        return jsonMapper.readerFor(Rules.class).readValue(json);
    }



    public abstract String getDefaultScopeId();

    public abstract Rules getRulesUnderTest();

    /**
     * eg 'google-workspace/gdrive'
     */
    public abstract String getYamlSerializationFilepath();

    public abstract String getExampleDirectoryPath();

    protected String asJson(String filePathWithinExampleDirectory) {
        return new String(TestUtils.getData(getExampleDirectoryPath() + "/" + filePathWithinExampleDirectory));
    }

    @SneakyThrows
    protected String sanitize(String endpoint, String jsonResponse) {
        return this.sanitizer.sanitize(new URL(endpoint), jsonResponse);
    }

    protected void assertNotSanitized(String content, Collection<String> shouldContain) {
        shouldContain.stream()
            .forEach(s -> assertTrue(content.contains(s), "Unsanitized content does not contain expected string: " + s));
    }
    protected void assertNotSanitized(String content, String... shouldContain) {
        assertNotSanitized(content, Arrays.asList(shouldContain));
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
                // remove wrapping
                doubleJsonEncodedPseudonym = StringUtils.unwrap(doubleJsonEncodedPseudonym, "\"");
                assertTrue(content.contains(doubleJsonEncodedPseudonym),
                    String.format("Sanitized does not contain %s, pseudonymized equivalent of %s", doubleJsonEncodedPseudonym, s));
            });
    }

    protected void assertPseudonymized(String content, String... shouldBePseudonymized) {
        assertPseudonymized(content, Arrays.asList(shouldBePseudonymized));
    }

    protected void assertPseudonymizedWithOriginal(String content, Collection<String> shouldBePseudonymized) {
        shouldBePseudonymized.stream()
            .forEach(s -> {
                String doubleJsonEncodedPseudonym =
                    sanitizer.getJsonConfiguration().jsonProvider().toJson(sanitizer.pseudonymizeWithOriginalToJson(s, sanitizer.getJsonConfiguration()));
                // remove wrapping
                doubleJsonEncodedPseudonym = StringUtils.unwrap(doubleJsonEncodedPseudonym, "\"");
                assertTrue(content.contains(doubleJsonEncodedPseudonym),
                    String.format("Sanitized does not contain %s, pseudonymized equivalent of %s", doubleJsonEncodedPseudonym, s));
            });
    }

    protected void assertPseudonymizedWithOriginal(String content, String... shouldBePseudonymized) {
        assertPseudonymizedWithOriginal(content, Arrays.asList(shouldBePseudonymized));
    }

    protected void assertContains(String content, String... shouldContain) {
        Arrays.stream(shouldContain)
            .forEach(s -> {
                assertTrue(content.contains(s), String.format("Sanitized does not contain '%s'", s));
            });
    }

    @SneakyThrows
    protected void assertUrlWithQueryParamsAllowed(String url) {
        assertTrue(sanitizer.isAllowed(new URL(url + "?param=value")), "single param blocked");
        assertTrue(sanitizer.isAllowed(new URL(url + "?param=value&param2=value2")), "multiple params blocked");
    }



    @SneakyThrows
    protected void assertUrlWithQueryParamsBlocked(String url) {
        assertFalse(sanitizer.isAllowed(new URL(url + "?param=value")), "query param allowed");
        assertFalse(sanitizer.isAllowed(new URL(url + "?param=value&param2=value2")), "multiple query params allowed");
    }

    @SneakyThrows
    protected void assertUrlWithSubResourcesAllowed(String url) {
        assertTrue(sanitizer.isAllowed(new URL(url + "/anypath")), "path blocked");
        assertTrue(sanitizer.isAllowed(new URL(url + "/anypath/anysubpath")), "path with subpath blocked");
    }

    @SneakyThrows
    protected void assertUrlWithSubResourcesBlocked(String url) {
        assertTrue(sanitizer.isAllowed(new URL(url + "/anypath")), "subpath allowed");
        assertTrue(sanitizer.isAllowed(new URL(url + "/anypath/anysubpath")), "2 subpaths allowed");
    }

    @SneakyThrows
    protected void assertUrlBlocked(String url) {
        assertFalse(sanitizer.isAllowed(new URL(url)), "rules allowed url that should be blocked: " + url);
    }

}
