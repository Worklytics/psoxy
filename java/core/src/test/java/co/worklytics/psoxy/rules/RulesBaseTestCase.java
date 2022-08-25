package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.MapFunction;
import dagger.Component;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;

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
    @Inject
    protected RulesUtils rulesUtils;

    @Getter @Setter
    RulesTestSpec testSpec = RulesTestSpec.builder().build();

    @Builder
    public static class RulesTestSpec {

        String sanitizedExamplesDirectoryPath;
        Optional<String> getSanitizedExamplesDirectoryPath() {
            return Optional.ofNullable(this.sanitizedExamplesDirectoryPath);
        }

        String yamlSerializationFilePath;

        Optional<String> getYamlSerializationFilePath() {
            return Optional.ofNullable(this.yamlSerializationFilePath);
        }
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    @Builder
    public static class InvocationExample {

        @NonNull
        String requestUrl;

        @NonNull
        String plainExampleFile;
    }



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

        sanitizer = sanitizerFactory.create(Sanitizer.ConfigurationOptions.builder()
            .rules(getRulesUnderTest())
            .defaultScopeId(getDefaultScopeId())
            //TODO: existing test cases presume this
            .pseudonymImplementation(PseudonymImplementation.LEGACY)
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
    @Test
    void testExamples() {
        getExamples()
            .forEach(example -> {
                String original =
                    new String(TestUtils.getData(getExampleDirectoryPath() + "/" + example.getPlainExampleFile()));
                String sanitized = sanitize(example.getRequestUrl(), original);

                String sanitizedFilepath = testSpec.getSanitizedExamplesDirectoryPath()
                    .orElse(getExampleDirectoryPath() + "/sanitized") + "/" + example.getPlainExampleFile();

                String expected = StringUtils.trim(new String(TestUtils.getData(sanitizedFilepath )));

                assertEquals(expected,
                    StringUtils.trim(prettyPrintJson(sanitized)), sanitizedFilepath + " does not match output");
            });
    }



    @SneakyThrows
    RuleSet yamlRoundtrip(RuleSet rules) {
        String yaml = yamlMapper.writeValueAsString(rules).replace("---\n", "");
        return yamlMapper.readerFor(rules.getClass()).readValue(yaml);
    }

    @SneakyThrows
    RuleSet jsonRoundtrip(RuleSet rules) {
        String json = jsonMapper.writeValueAsString(rules);
        return jsonMapper.readerFor(rules.getClass()).readValue(json);
    }



    public abstract String getDefaultScopeId();

    public abstract RuleSet getRulesUnderTest();

    /**
     * eg 'google-workspace/gdrive'
     */
    public abstract String getYamlSerializationFilepath();

    public abstract String getExampleDirectoryPath();



    public Stream<InvocationExample> getExamples() {
        return Stream.empty();
    }

    protected String asJson(String filePathWithinExampleDirectory) {
        return asJson(getExampleDirectoryPath(), filePathWithinExampleDirectory);
    }
    protected String asJson(String directoryPath, String filePathWithinExampleDirectory) {
        return new String(TestUtils.getData(directoryPath + "/" + filePathWithinExampleDirectory));
    }

    @SneakyThrows
    protected String sanitize(String endpoint, String jsonResponse) {
        return this.sanitizer.sanitize(new URL(endpoint), jsonResponse);
    }

    protected void assertSha(String expectedSha) {
        assertNotNull(expectedSha);
        assertEquals(expectedSha, rulesUtils.sha(sanitizer.getConfigurationOptions().getRules()));
    }

    protected void assertNotSanitized(String content, Collection<String> shouldContain) {
        shouldContain
            .forEach(s -> assertTrue(content.contains(s), String.format("Unsanitized content does not contain expected string: %s\n%s", s, prettyPrintJson(content))));
    }
    protected void assertNotSanitized(String content, String... shouldContain) {
        assertNotSanitized(content, Arrays.asList(shouldContain));
    }

    protected void assertRedacted(String content, Collection<String> shouldNotContain) {
        shouldNotContain
            .forEach(s -> assertFalse(content.contains(s), String.format("Sanitized content still contains: %s\n%s", s, prettyPrintJson(content))));

        shouldNotContain
            .forEach(s -> assertFalse(content.contains(sanitizer.pseudonymizeToJson(s, sanitizer.getJsonConfiguration())),
                "Sanitized contains pseudonymized equivalent of: " + s));
    }
    protected void assertRedacted(String content, String... shouldNotContain) {
        assertRedacted(content, Arrays.asList(shouldNotContain));
    }

    protected String context(String haystack, String needle) {
        int start = Math.max(0, haystack.indexOf(needle) - 50);
        int end = Math.min(haystack.length(), haystack.indexOf(needle) + needle.length() + 50);
        return haystack.substring(start, end);
    }

    protected void assertPseudonymized(String content, String ... shouldBePseudonymized) {
        assertPseudonymized(content, List.of(shouldBePseudonymized));
    }

    protected void assertPseudonymized(String content, Collection<String> shouldBePseudonymized) {
        shouldBePseudonymized
            .forEach(s ->
                assertFalse(content.contains(s), () -> String.format("Sanitized content still contains unpseudonymized: %s at %s", s, this.context(content, s))));

        shouldBePseudonymized
            .forEach(s -> {
                String doubleJsonEncodedPseudonym =
                    sanitizer.getJsonConfiguration().jsonProvider().toJson(sanitizer.pseudonymizeToJson(s, sanitizer.getJsonConfiguration()));
                // remove wrapping
                doubleJsonEncodedPseudonym = StringUtils.unwrap(doubleJsonEncodedPseudonym, "\"");
                assertTrue(content.contains(doubleJsonEncodedPseudonym),
                    String.format("Sanitized does not contain %s, pseudonymized equivalent of %s", doubleJsonEncodedPseudonym, s));
            });
    }

    protected void assertReversibleUrlTokenized(String content, Collection<String> shouldBeTransformed) {
        assertTransformed(content, shouldBeTransformed, Transform.Pseudonymize.builder()
            .includeReversible(true)
            .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .build());
    }

    protected void assertTransformed(String content, Collection<String> shouldBeTransformed, Transform transform) {
        shouldBeTransformed
            .forEach(s ->
                assertFalse(content.contains(s), () -> String.format("Sanitized content still contains untransformed: %s at %s", s, this.context(content, s))));

        shouldBeTransformed
            .forEach(s -> {
                MapFunction f = sanitizer.getTransformImpl(transform);

                String expected = sanitizer.getJsonConfiguration().jsonProvider().toJson(f.map(s, sanitizer.getJsonConfiguration()));

                assertTrue(content.contains(expected),
                    String.format("Sanitized does not contain %s, transformed equivalent of %s", expected, s));
            });
    }

    protected void assertPseudonymizedWithOriginal(String content, Collection<String> shouldBePseudonymized) {
        shouldBePseudonymized
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

    @SneakyThrows
    protected void assertUrlWithQueryParamsAllowed(String url) {
        assertTrue(sanitizer.isAllowed(new URL(url + "?param=value")), "single param blocked");
        assertTrue(sanitizer.isAllowed(new URL(url + "?param=value&param2=value2")), "multiple params blocked");
    }

    @SneakyThrows
    protected void assertUrlAllowed(String url) {
        assertTrue(sanitizer.isAllowed(new URL(url)), "api endpoint url blocked");
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
        assertFalse(sanitizer.isAllowed(new URL(url + "/anypath")), "subpath allowed");
        assertFalse(sanitizer.isAllowed(new URL(url + "/anypath/anysubpath")), "2 subpaths allowed");
    }

    @SneakyThrows
    protected void assertUrlBlocked(String url) {
        assertFalse(sanitizer.isAllowed(new URL(url)), "rules allowed url that should be blocked: " + url);
    }

    /**
     * Utility method to print out formatted JSON for debug easily
     *
     *
     *
     *
     * @param json
     * @return
     */
    @SneakyThrows
    @SuppressWarnings("unused")
    protected String prettyPrintJson(String json) {

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
            .withoutSpacesInObjectEntries();
        printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);


        return jsonMapper
            .writer()
            .with(printer)
            .writeValueAsString(jsonMapper.readerFor(Object.class).readValue(json));

        //NOTE: Gson seems to URL-encode embedded strings!?!?!
        //  eg "64123avdfsMVA==" --> "64123avdfsMVA\u0030\0030"
        // Gson gson = new GsonBuilder().setPrettyPrinting().create();
        // return gson.toJson(JsonParser.parseString(json));
    }

    /**
     * asserts equivalence of two strings after round-trips through Jackson, so any failure is more
     * readable than comparing non-pretty JSON, and any differences in original formatting (rather
     * than actual JSON structure/content) are ignored. eg, expected/actual can have different
     * "pretty" formatting, or one may not have "pretty" formatting at all.
     *
     * @param expected output value of test
     * @param actual output value of test
     */
    protected void assertJsonEquals(String expected, String actual) {
        assertEquals(prettyPrintJson(expected), prettyPrintJson(actual));
    }

}
