package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

class GeminiForWorkspaceTests extends JavaRulesTestBaseCase {

    @SneakyThrows
    public RESTRules getRulesUnderTest() {
        return yamlMapper.readValue(
            this.getClass().getClassLoader().getResourceAsStream("sources/google-workspace/gemini-for-workspace/gemini-for-workspace.yaml"),
            Rules2.class
        );
    }

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("google-workspace")
        .defaultScopeId("gapps")
        .sourceKind("gemini-for-workspace")
        .build();

    @SneakyThrows
    @Test
    void activities() {
        String endpoint = "https://admin.googleapis.com/admin/reports/v1/activity/users/123/applications/gemini_for_workspace";
        String jsonString = asJson("admin_reports_v1_activity.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "alice@acme.com",
            "23.18.19.121"
         );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize("GET", new URL(endpoint), jsonString);

        assertPseudonymized(sanitized, PII);


        assertUrlAllowed(endpoint);
        assertUrlAllowed("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/gemini_for_workspace");
        assertUrlAllowed("https://admin.googleapis.com/admin/reports/v1/activity/users/12341/applications/gemini_for_workspace");
        assertUrlBlocked("https://admin.googleapis.com/admin/reports/v1/activity/users/alice@acme.com/applications/gemini_for_workspace");
    }

    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://admin.googleapis.com/admin/reports/v1/activity/users/123/applications/gemini_for_workspace", "admin_reports_v1_activity.json")
        );
    }
}
