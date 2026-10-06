package co.worklytics.psoxy.impl;

import com.avaulta.gateway.resources.ResourceService;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.augments.AugmentValidation;
import com.avaulta.gateway.rules.augments.ClassifyProcessor;
import com.avaulta.gateway.rules.augments.GenMetadataProcessor;
import com.avaulta.gateway.rules.augments.SentenceMetadataProcessor;
import com.avaulta.gateway.rules.augments.UnavailableGenMetadataBackend;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof for the GitHub AI-authorship attribution use-case using production-shaped
 * {@code regexExtract} YAML (under {@code alpha-features/}) — not wired into prebuilt GitHub rules.
 */
class RegexExtractAiAttributionAugmentTest {

    private static final String COMMIT_AUGMENT_YAML =
        "alpha-features/github-ai-attribution/commit-message-regex-extract.yaml";
    private static final String PULL_AUGMENT_YAML =
        "alpha-features/github-ai-attribution/pull-request-regex-extract.yaml";

    ObjectMapper objectMapper;
    ObjectMapper yamlMapper;
    AugmentProcessor augmentProcessor;
    Augment.RegexExtract commitMessageAugment;
    Augment.RegexExtract pullRequestAugment;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        yamlMapper = new ObjectMapper(new YAMLFactory());
        Configuration jsonConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();
        ResourceService noModels = path -> Optional.empty();
        GenMetadataProcessor genMetadataProcessor =
            new GenMetadataProcessor(new UnavailableGenMetadataBackend(), objectMapper, 2,
                new JsonSchemaValidationUtils());
        ClassifyProcessor classifyProcessor =
            new ClassifyProcessor(new UnavailableGenMetadataBackend(), objectMapper, 2);
        augmentProcessor = new AugmentProcessor(jsonConfiguration,
            new JsonSchemaValidationUtils(),
            objectMapper,
            new SentenceMetadataProcessor(noModels),
            genMetadataProcessor,
            classifyProcessor);

        commitMessageAugment = loadAugment(COMMIT_AUGMENT_YAML);
        pullRequestAugment = loadAugment(PULL_AUGMENT_YAML);
        AugmentValidation.validateAugments(List.of(commitMessageAugment, pullRequestAugment));
    }

    @Test
    void exampleYaml_deserializesAsRegexExtract() {
        assertEquals("regexExtract", commitMessageAugment.getFunctionName());
        assertEquals(List.of("$..commit.message"), commitMessageAugment.getJsonPaths());
        assertTrue(commitMessageAugment.getExtractions().containsKey("aiAssistPlatform"));
        assertTrue(commitMessageAugment.getExtractions().containsKey("commitType"));

        assertEquals(List.of("$[*]"), pullRequestAugment.getJsonPaths());
        assertEquals(List.of("title", "body", "commit_message"), pullRequestAugment.getSourceFields());
    }

    @Test
    void commitList_cursorTrailer_attributedBeforeMessageRedaction() {
        List<Map<String, Object>> commits = List.of(
            commit("abc1", "fix(proxy): handle empty path\n\nMade with Cursor"),
            commit("abc2", "feat: add widget\n\nCo-Authored-By: Cursor <cursoragent@cursor.com>"));

        augmentProcessor.applyAugments(List.of(commitMessageAugment), commits);

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) commits.get(0).get("commit");
        assertEquals("fix(proxy): handle empty path\n\nMade with Cursor", first.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> firstMeta = (Map<String, Object>) first.get("+message:regexExtract");
        assertEquals("Cursor", firstMeta.get("aiAssistPlatform"));
        assertEquals("fix", firstMeta.get("commitType"));
        assertFalse(firstMeta.containsKey("aiAssistModel"));

        @SuppressWarnings("unchecked")
        Map<String, Object> secondMeta = (Map<String, Object>) ((Map<?, ?>) commits.get(1).get("commit"))
            .get("+message:regexExtract");
        assertEquals("Cursor", secondMeta.get("aiAssistPlatform"));
        assertEquals("feature", secondMeta.get("commitType"));
    }

    @Test
    void commitList_claudeAndCopilotTrailers() {
        List<Map<String, Object>> commits = List.of(
            commit("c1", "feat(auth): refresh tokens\n\n"
                + "Co-Authored-By: Claude <noreply@anthropic.com>\nmodel: claude-sonnet-4"),
            commit("c2", "fix: auto-merge\n\nCopilot coding-agent"),
            commit("c3", "chore: deps\n\nCo-Authored-By: GitHub Copilot <copilot-swe-agent@users.noreply.github.com>"));

        augmentProcessor.applyAugments(List.of(commitMessageAugment), commits);

        @SuppressWarnings("unchecked")
        Map<String, Object> claude = (Map<String, Object>) ((Map<?, ?>) commits.get(0).get("commit"))
            .get("+message:regexExtract");
        assertEquals("Claude Code", claude.get("aiAssistPlatform"));
        assertEquals("claude-sonnet-4", claude.get("aiAssistModel"));
        assertEquals("feature", claude.get("commitType"));

        @SuppressWarnings("unchecked")
        Map<String, Object> copilotAgent = (Map<String, Object>) ((Map<?, ?>) commits.get(1).get("commit"))
            .get("+message:regexExtract");
        assertEquals("Copilot", copilotAgent.get("aiAssistPlatform"));
        assertEquals("fix", copilotAgent.get("commitType"));

        @SuppressWarnings("unchecked")
        Map<String, Object> copilotCoauthored = (Map<String, Object>) ((Map<?, ?>) commits.get(2).get("commit"))
            .get("+message:regexExtract");
        assertEquals("Copilot", copilotCoauthored.get("aiAssistPlatform"));
        assertEquals("other", copilotCoauthored.get("commitType"));
    }

    @Test
    void commitList_humanCommit_conventionalTypeOnly() {
        List<Map<String, Object>> commits = List.of(
            commit("human", "Fix all the bugs"));

        augmentProcessor.applyAugments(List.of(commitMessageAugment), commits);

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) ((Map<?, ?>) commits.get(0).get("commit"))
            .get("+message:regexExtract");
        assertEquals("fix", meta.get("commitType"));
        assertFalse(meta.containsKey("aiAssistPlatform"));
        assertFalse(meta.containsKey("aiAssistModel"));
    }

    @Test
    void commitList_noSignals_omitsAugmentProperty() {
        List<Map<String, Object>> commits = List.of(
            commit("plain", "update readme"));

        augmentProcessor.applyAugments(List.of(commitMessageAugment), commits);

        @SuppressWarnings("unchecked")
        Map<String, Object> commit = (Map<String, Object>) commits.get(0).get("commit");
        assertNull(commit.get("+message:regexExtract"));
    }

    @Test
    void pullRequestList_titleBodyAndSquashMessage() {
        List<Map<String, Object>> pulls = List.of(
            pull(1, "feat: export metrics", "Adds a Prometheus endpoint.", null),
            pull(2, "fix: null deref", "Closes #99.",
                "fix(core): guard null\n\nMade with Cursor"),
            pull(3, "docs: attribution", "Explains declared AI trailers.",
                "Generated with Claude Code"));

        augmentProcessor.applyAugments(List.of(pullRequestAugment), pulls);

        @SuppressWarnings("unchecked")
        Map<String, Object> feature = (Map<String, Object>) pulls.get(0).get("+self:regexExtract");
        assertEquals("Feature", feature.get("prCategory"));
        assertFalse(feature.containsKey("aiAssistPlatform"));

        @SuppressWarnings("unchecked")
        Map<String, Object> bugfixCursor = (Map<String, Object>) pulls.get(1).get("+self:regexExtract");
        assertEquals("Bugfix", bugfixCursor.get("prCategory"));
        assertEquals("Cursor", bugfixCursor.get("aiAssistPlatform"));

        @SuppressWarnings("unchecked")
        Map<String, Object> docsClaude = (Map<String, Object>) pulls.get(2).get("+self:regexExtract");
        assertEquals("Docs", docsClaude.get("prCategory"));
        assertEquals("Claude Code", docsClaude.get("aiAssistPlatform"));
    }

    @Test
    void realisticRepoCommitsPage_matchesGitHubListShape() throws Exception {
        Map<String, Object> page = loadJson("alpha-features/github-ai-attribution/example-repo-commits.json");

        augmentProcessor.applyAugments(List.of(commitMessageAugment), page);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> cursorCommit = (Map<String, Object>) items.get(0).get("commit");
        @SuppressWarnings("unchecked")
        Map<String, Object> cursorMeta = (Map<String, Object>) cursorCommit.get("+message:regexExtract");
        assertEquals("Cursor", cursorMeta.get("aiAssistPlatform"));
        assertEquals("fix", cursorMeta.get("commitType"));

        @SuppressWarnings("unchecked")
        Map<String, Object> humanCommit = (Map<String, Object>) items.get(2).get("commit");
        assertNull(humanCommit.get("+message:regexExtract"));
    }

    private Augment.RegexExtract loadAugment(String resourcePath) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            Augment augment = yamlMapper.readValue(in, Augment.class);
            assertInstanceOf(Augment.RegexExtract.class, augment);
            return (Augment.RegexExtract) augment;
        }
    }

    private Map<String, Object> loadJson(String resourcePath) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        }
    }

    private static Map<String, Object> commit(String sha, String message) {
        Map<String, Object> commit = new LinkedHashMap<>();
        commit.put("message", message);
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("sha", sha);
        wrapper.put("commit", commit);
        return wrapper;
    }

    private static Map<String, Object> pull(int number, String title, String body, String commitMessage) {
        Map<String, Object> pull = new LinkedHashMap<>();
        pull.put("number", number);
        pull.put("title", title);
        pull.put("body", body);
        if (commitMessage != null) {
            pull.put("commit_message", commitMessage);
        }
        return pull;
    }
}
