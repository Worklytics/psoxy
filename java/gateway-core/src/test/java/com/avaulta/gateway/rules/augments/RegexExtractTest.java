package com.avaulta.gateway.rules.augments;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegexExtractTest {

    static Augment.RegexExtract.ExtractionRule literalRule(String regex, String value) {
        return Augment.RegexExtract.ExtractionRule.builder()
            .regex(regex)
            .value(value)
            .build();
    }

    static Augment.RegexExtract.ExtractionRule groupRule(String regex, int group) {
        return Augment.RegexExtract.ExtractionRule.builder()
            .regex(regex)
            .group(group)
            .build();
    }

    @Test
    void compute_detectsCursorAttribution() {
        Augment.RegexExtract augment = Augment.RegexExtract.builder()
            .extraction("aiAssistPlatform", List.of(
                literalRule("(?i)Made with Cursor", "Cursor"),
                literalRule("(?i)Co-Authored-By:\\s*Cursor", "Cursor"),
                literalRule("(?i)cursoragent@cursor\\.com", "Cursor")))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) augment.compute(
            "fix: lint\n\nMade with Cursor");

        assertEquals("Cursor", result.get("aiAssistPlatform"));
    }

    @Test
    void compute_detectsClaudeAttribution() {
        Augment.RegexExtract augment = Augment.RegexExtract.builder()
            .extraction("aiAssistPlatform", List.of(
                literalRule("(?i)Co-Authored-By:\\s*Claude", "Claude Code"),
                literalRule("(?i)Generated with Claude Code", "Claude Code"),
                literalRule("(?i)noreply@anthropic\\.com", "Claude Code")))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) augment.compute(
            "feat: thing\n\nCo-Authored-By: Claude <noreply@anthropic.com>");

        assertEquals("Claude Code", result.get("aiAssistPlatform"));
    }

    @Test
    void compute_detectsCopilotAttribution() {
        Augment.RegexExtract augment = Augment.RegexExtract.builder()
            .extraction("aiAssistPlatform", List.of(
                literalRule("(?i)Copilot coding-agent", "Copilot"),
                literalRule("(?i)copilot-swe-agent", "Copilot")))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) augment.compute(
            "fix: auto\n\nCopilot coding-agent");

        assertEquals("Copilot", result.get("aiAssistPlatform"));
    }

    @Test
    void compute_omitsUnmatchedFields() {
        Augment.RegexExtract augment = Augment.RegexExtract.builder()
            .extraction("aiAssistPlatform", List.of(
                literalRule("(?i)Made with Cursor", "Cursor")))
            .extraction("aiAssistModel", List.of(
                groupRule("(?i)model:\\s*(\\S+)", 1)))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) augment.compute("Fix all the bugs");

        assertNull(result);
    }

    @Test
    void compute_classifiesConventionalCommit() {
        Augment.RegexExtract augment = Augment.RegexExtract.builder()
            .extraction("commitType", List.of(
                literalRule("(?i)^(?:fix|bugfix)(?:\\(|:|\\s)", "fix"),
                literalRule("(?i)^(?:feat|feature)(?:\\(|:|\\s)", "feature")))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) augment.compute("Fix all the bugs");

        assertEquals("fix", result.get("commitType"));
    }

    @Test
    void compute_concatenatesSourceFieldsForObjectInput() {
        Augment.RegexExtract augment = Augment.RegexExtract.builder()
            .sourceField("title")
            .sourceField("body")
            .extraction("prCategory", List.of(
                literalRule("(?i)^feat(?:\\(|:|\\s)", "Feature"),
                literalRule("(?i)\\bbug\\b", "Bugfix")))
            .build();

        Map<String, Object> pull = new LinkedHashMap<>();
        pull.put("title", "feat: add widget");
        pull.put("body", "Implements the widget.");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) augment.compute(pull);

        assertEquals("Feature", result.get("prCategory"));
    }

    @Test
    void compute_extractsCaptureGroup() {
        Augment.RegexExtract augment = Augment.RegexExtract.builder()
            .extraction("aiAssistModel", List.of(
                groupRule("(?i)model:\\s*([\\w./+-]+)", 1)))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) augment.compute(
            "chore: bump\n\nmodel: claude-sonnet-4");

        assertEquals("claude-sonnet-4", result.get("aiAssistModel"));
    }
}
