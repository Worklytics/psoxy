package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.impl.RESTApiSanitizerImpl;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.transforms.Transform;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.worklytics.test.TestUtils.prettyPrintJson;
import static org.junit.jupiter.api.Assertions.*;

/**
 * abstract test stuff for Rules implementations
 *
 * re-use through inheritance, so rather inflexible
 * q: better as junit Extension or something? how do to that
 *
 */
abstract public class RulesBaseTestCase {

    protected RESTApiSanitizerImpl sanitizer;

    @Inject
    protected ObjectMapper jsonMapper;
    @Inject @Named("ForYAML")
    protected ObjectMapper yamlMapper;
    @Inject
    protected RESTApiSanitizerFactory sanitizerFactory;
    @Inject
    protected PseudonymizerImplFactory pseudonymizerFactory;
    @Inject
    protected RulesUtils rulesUtils;
    @Inject
    protected Validator validator;

    @Inject
    protected UrlSafeTokenPseudonymEncoder urlSafeTokenPseudonymEncoder;

    public abstract RESTRules getRulesUnderTest();

    public abstract RulesTestSpec getRulesTestSpec();


    @With
    @Builder
    public static class RulesTestSpec {

        String sourceFamily; //eg, google-workspace

        @NonNull
        String sourceKind; //eg, gdrive

        /**
         * path within sourceDocsRoot to directory containing example API response for this test
         * case, including trailing '/'
         * (null if no example responses)
         */
        @Builder.Default
        String exampleApiResponsesDirectoryPath = "example-api-responses/original/";

        String exampleApiResponsesDirectoryPathFull;


        public String getExampleApiResponsesDirectoryPathFull() {
            return Optional.ofNullable(exampleApiResponsesDirectoryPathFull)
                .orElse(sourceDocsRoot() + exampleApiResponsesDirectoryPath);
        }

        /**
         * path within sourceDocsRoot to directory containing example API response for this test
         * case, including trailing '/'
         *
         */
        @Builder.Default
        String exampleSanitizedApiResponsesPath = "example-api-responses/sanitized/";

        String exampleSanitizedApiResponsesPathFull;

        public String getExampleSanitizedApiResponsesPathFull() {
            return Optional.ofNullable(exampleSanitizedApiResponsesPathFull)
                .orElse(sourceDocsRoot() + exampleSanitizedApiResponsesPath);
        }

        String rulesFile;

        String getRulesFile() {
            return Optional.ofNullable(rulesFile).orElse(sourceKind);
        }
        public String getRulesFilePathFull() {
            return sourceDocsRoot() + getRulesFile() + ".yaml";
        }

        /**
         * @return path to root, with trailing '/'
         */
        private String sourceDocsRoot() {
            return "sources/" +
                Arrays.asList(
                        sourceFamily,
                        sourceKind // never null
                    ).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("/"))
                + "/";
        }


        String defaultScopeId;

        String getDefaultScopeId() {
            return Optional.ofNullable(defaultScopeId).orElse(sourceKind);
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
        MockModules.ForSecretStore.class,
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

        sanitizer = sanitizerFactory.create(getRulesUnderTest(),
            pseudonymizerFactory.create(Pseudonymizer.ConfigurationOptions.builder()
            .defaultScopeId(getRulesTestSpec().getDefaultScopeId())
            .pseudonymImplementation(PseudonymImplementation.DEFAULT)
            .build()));

        //q: good way to also batch test sanitizers from yaml/json formats of rules, to ensure
        // serialization doesn't materially change any behavior??
    }

    @Test
    void validate() {
        validator.validate(getRulesUnderTest());
    }

    @SneakyThrows
    @Test
    void validateYaml() {
        validator.validate(yamlRoundtrip(getRulesUnderTest()));
    }

    // regular param --> 4096
    int REGULAR_SSM_PARAM_LIMIT = 4096;
    int ADVANCED_SSM_PARAM_LIMIT = 8192;

    @SneakyThrows
    @Test
    public void yamlLength() {
        int rulesLengthInChars = yamlMapper.writeValueAsString(getRulesUnderTest()).length();
        assertTrue(rulesLengthInChars < ADVANCED_SSM_PARAM_LIMIT, "YAML rules " + rulesLengthInChars + " chars long; want < " + ADVANCED_SSM_PARAM_LIMIT + " chars to fit as AWS SSM param");
    }

    @SneakyThrows
    @Test
    void yamlLengthCompressed() {
        int rulesLengthInChars = TestUtils.asBase64Gzipped(yamlMapper.writeValueAsString(getRulesUnderTest())).length();
        assertTrue(rulesLengthInChars < REGULAR_SSM_PARAM_LIMIT,
            "YAML rules " + rulesLengthInChars + " chars long; want < " + REGULAR_SSM_PARAM_LIMIT + " chars to fit as AWS SSM param");
    }

    @SneakyThrows
    @Test
    void validateJSON() {
        validator.validate(jsonRoundtrip(getRulesUnderTest()));
    }

    @SneakyThrows
    @Test
    void testExamples() {
        getExamples()
            .forEach(example -> {
                String original =
                    new String(TestUtils.getData(getRulesTestSpec().getExampleApiResponsesDirectoryPathFull() + example.getPlainExampleFile()));
                String sanitized = sanitize(example.getRequestUrl(), original);

                String sanitizedFilepath = getRulesTestSpec().getExampleSanitizedApiResponsesPathFull() + example.getPlainExampleFile();


                String expected = StringUtils.trim(new String(TestUtils.getData(sanitizedFilepath)));

                assertEquals(expected,
                    StringUtils.trim(prettyPrintJson(sanitized)), sanitizedFilepath + " does not match output");
            });
    }



    @SneakyThrows
    com.avaulta.gateway.rules.RuleSet yamlRoundtrip(com.avaulta.gateway.rules.RuleSet rules) {
        String yaml = yamlMapper.writeValueAsString(rules).replace("---\n", "");
        return yamlMapper.readerFor(rules.getClass()).readValue(yaml);
    }

    @SneakyThrows
    com.avaulta.gateway.rules.RuleSet jsonRoundtrip(com.avaulta.gateway.rules.RuleSet rules) {
        String json = jsonMapper.writeValueAsString(rules);
        return jsonMapper.readerFor(rules.getClass()).readValue(json);
    }

    public Stream<InvocationExample> getExamples() {
        return Stream.empty();
    }

    protected String asJson(String filePathWithinExampleDirectory) {
        return asJson(getRulesTestSpec().getExampleApiResponsesDirectoryPathFull(), filePathWithinExampleDirectory);
    }
    protected String asJson(String directoryPath, String filePathWithinExampleDirectory) {
        if (!directoryPath.endsWith("/")) {
            directoryPath = directoryPath + "/";
        }

        return new String(TestUtils.getData(directoryPath + filePathWithinExampleDirectory));
    }

    @SneakyThrows
    protected String sanitize(String endpoint, String jsonResponse) {
        return this.sanitizer.sanitize("GET", new URL(endpoint), jsonResponse);
    }

    protected void assertSha(String expectedSha) {
        assertNotNull(expectedSha);
        assertEquals(expectedSha, rulesUtils.sha(sanitizer.getRules()));
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

    static Transform.PseudonymizationTransform NO_ORIG_INCLUDE_REVERSIBLE = new Transform.PseudonymizationTransform() {
        @Override
        public Boolean getIncludeOriginal() {
            return false;
        }

        @Override
        public Boolean getIncludeReversible() {
            return true;
        }
    };

    protected void assertPseudonymized(String content, Collection<String> shouldBePseudonymized) {
        shouldBePseudonymized
            .forEach(s ->
                assertFalse(content.contains(s), () -> String.format("Sanitized content still contains unpseudonymized: %s at %s", s, this.context(content, s))));

        List<MapFunction> possiblePseudonymizations = Arrays.asList(
            sanitizer.getPseudonymize(Transform.Pseudonymize.builder().encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN).build()),
            sanitizer.getPseudonymize(Transform.Pseudonymize.builder().encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN).includeReversible(true).build()),
            sanitizer.getPseudonymizeRegexMatches(Transform.PseudonymizeRegexMatches.builder().regex(".*").includeReversible(false).build()),
            sanitizer.getPseudonymizeRegexMatches(Transform.PseudonymizeRegexMatches.builder().regex(".*").includeReversible(true).build())
        );



        shouldBePseudonymized
            .forEach(s -> {
                //JSON
                String doubleJsonEncodedPseudonym =
                    sanitizer.getJsonConfiguration().jsonProvider().toJson(sanitizer.pseudonymizeToJson(s, sanitizer.getJsonConfiguration()));

                List<String> serializedPseudonyms =
                    possiblePseudonymizations.stream()
                        .map(f -> (String) f.map(s, sanitizer.getJsonConfiguration()))
                        .collect(Collectors.toList());

                if (serializedPseudonyms.stream().anyMatch(serialized -> serialized.length() < 20)) {
                    throw new IllegalArgumentException("Pseudonymization of " + s + " is too short: " + serializedPseudonyms);
                }


                // remove wrapping
                doubleJsonEncodedPseudonym = StringUtils.unwrap(doubleJsonEncodedPseudonym, "\"");
                assertTrue(content.contains(doubleJsonEncodedPseudonym)
                        || serializedPseudonyms.stream().anyMatch(content::contains),
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
        assertTrue(sanitizer.isAllowed("GET", new URL(url + "?param=value")), "single param blocked");
        assertTrue(sanitizer.isAllowed("GET", new URL(url + "?param=value&param2=value2")), "multiple params blocked");
    }

    @SneakyThrows
    protected void assertUrlAllowed(String url) {
        assertTrue(sanitizer.isAllowed("GET", new URL(url)), "api endpoint url blocked");
    }

    @SneakyThrows
    protected void assertUrlWithQueryParamsBlocked(String url) {
        assertFalse(sanitizer.isAllowed("GET", new URL(url + "?param=value")), "query param allowed");
        assertFalse(sanitizer.isAllowed("GET", new URL(url + "?param=value&param2=value2")), "multiple query params allowed");
    }

    @SneakyThrows
    protected void assertUrlWithSubResourcesAllowed(String url) {
        assertTrue(sanitizer.isAllowed("GET", new URL(url + "/anypath")), "path blocked");
        assertTrue(sanitizer.isAllowed("GET", new URL(url + "/anypath/anysubpath")), "path with subpath blocked");
    }

    @SneakyThrows
    protected void assertUrlWithSubResourcesBlocked(String url) {
        assertFalse(sanitizer.isAllowed("GET", new URL(url + "/anypath")), "subpath allowed");
        assertFalse(sanitizer.isAllowed("GET", new URL(url + "/anypath/anysubpath")), "2 subpaths allowed");
    }

    @SneakyThrows
    protected void assertUrlBlocked(String url) {
        assertFalse(sanitizer.isAllowed("GET", new URL(url)), "rules allowed url that should be blocked: " + url);
    }

    @Deprecated // used TestUtils::assertJsonEquals directly
    protected void assertJsonEquals(String expected, String actual) {
        TestUtils.assertJsonEquals(expected, actual);
    }
}
