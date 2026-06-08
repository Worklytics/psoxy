package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchemaFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Opt-in helpers for local Jlama integration tests.
 */
final class GenMetadataIntegrationSupport {

    static final String ENABLE_PROPERTY = "psoxy.genMetadata.integration";
    static final String MODEL_CACHE_ENV = "PSOXY_GEN_MODEL_CACHE";
    static final String MODEL_ID_ENV = "PSOXY_GEN_MODEL";

    private GenMetadataIntegrationSupport() {
    }

    static boolean enabled() {
        if ("true".equalsIgnoreCase(System.getProperty(ENABLE_PROPERTY))) {
            return true;
        }
        String env = System.getenv("PSOXY_GEN_INTEGRATION_TEST");
        return "true".equalsIgnoreCase(env);
    }

    static GenMetadataConfig defaultConfig() {
        String modelId = Optional.ofNullable(System.getenv(MODEL_ID_ENV))
            .filter(s -> !s.isBlank())
            .orElse(GenMetadataConfig.DEFAULT_MODEL);
        return GenMetadataConfig.builder()
            .backend(GenMetadataConfig.BACKEND_LOCAL)
            .modelId(modelId.trim())
            .timeoutSeconds(120)
            .maxInputChars(4096)
            .maxTokens(256)
            .build();
    }

    static Path modelCacheDir(GenMetadataConfig config) {
        String override = System.getenv(MODEL_CACHE_ENV);
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        return Path.of(System.getProperty("user.home"), ".jlama");
    }

    static boolean isModelMaterialized(GenMetadataConfig config, Path cacheDir) {
        if (Files.isRegularFile(cacheDir.resolve(config.localModelCacheDirName()).resolve("config.json"))) {
            return true;
        }
        if (Files.isRegularFile(cacheDir.resolve(config.getModelId().replace("/", "_")).resolve("config.json"))) {
            return true;
        }
        if (Files.isRegularFile(cacheDir.resolve(config.getModelId()).resolve("config.json"))) {
            return true;
        }
        try (var stream = Files.list(cacheDir)) {
            return stream.anyMatch(path -> Files.isRegularFile(path.resolve("config.json")));
        } catch (Exception e) {
            return false;
        }
    }

    /** MS Copilot PoC classification task (mirrors {@code PrebuiltSanitizerRules}). */
    static String copilotTaskPrompt() {
        return """
            Classify the user's LLM prompt (input text) into exactly one category.
            Use one of these category names verbatim:
            Email Drafting, General Content Drafting, Email Editing and Refinement,
            General Content Editing and Refinement, Summarization and Synthesis,
            Research and Ideation, Analysis and Data Work, Code Generation and Development,
            Workflow and Task Management, Uncategorized, Excluded.
            Use "Uncategorized" when the prompt is substantive but does not clearly fit a specific category.
            Use "Excluded" for social niceties (greetings, thanks, pleasantries only) or prompts
            that are too short or non-substantive to classify.
            """;
    }

    static JsonSchemaFilter copilotOutputSchema() {
        return JsonSchemaFilter.builder()
            .type("object")
            .required(List.of("category"))
            .properties(Map.of(
                "category", JsonSchemaFilter.builder()
                    .type("string")
                    .enumValues(List.of(
                        "Email Drafting",
                        "General Content Drafting",
                        "Email Editing and Refinement",
                        "General Content Editing and Refinement",
                        "Summarization and Synthesis",
                        "Research and Ideation",
                        "Analysis and Data Work",
                        "Code Generation and Development",
                        "Workflow and Task Management",
                        "Uncategorized",
                        "Excluded"))
                    .build()))
            .build();
    }

    static final List<String> COPILOT_CATEGORIES = List.of(
        "Email Drafting",
        "General Content Drafting",
        "Email Editing and Refinement",
        "General Content Editing and Refinement",
        "Summarization and Synthesis",
        "Research and Ideation",
        "Analysis and Data Work",
        "Code Generation and Development",
        "Workflow and Task Management",
        "Uncategorized",
        "Excluded");

    /**
     * Realistic short Copilot research prompt (typical interaction size).
     */
    static String shortResearchIdeationPrompt() {
        return """
            Help me brainstorm differentiated AI analytics workflows for enterprise buyers who care \
            about privacy and governance. Compare two competitors and suggest discovery questions \
            for customer interviews about research and ideation use cases.
            """;
    }

    /**
     * Long Copilot user prompt (~3500 chars) that exceeded the misconfigured Jlama budget in live testing.
     */
    static String longResearchIdeationPrompt() {
        String paragraph = """
            Help me brainstorm a product strategy for our team analytics platform. I want to compare \
            how competitors position AI-assisted insights, identify three differentiated workflows we \
            could build this quarter, and outline risks if we rely too heavily on automated summaries \
            without human review. Include examples of enterprise buyers who care about privacy, \
            governance, and export controls. Suggest discovery questions I should ask customer interviews \
            next week about how they use Copilot-style assistants for research and ideation sessions.
            """;
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 3500) {
            sb.append(paragraph);
            if (sb.length() < 3500) {
                sb.append('\n');
            }
        }
        return sb.substring(0, 3500);
    }

    static String promptOfLength(int chars) {
        String seed = "Analyze quarterly revenue trends and recommend actions. ";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < chars) {
            sb.append(seed);
        }
        return sb.substring(0, chars);
    }

    static boolean messageChainContains(Throwable t, String fragment) {
        while (t != null) {
            if (t.getMessage() != null && t.getMessage().contains(fragment)) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
