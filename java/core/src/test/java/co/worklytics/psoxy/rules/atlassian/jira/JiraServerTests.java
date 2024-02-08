package co.worklytics.psoxy.rules.atlassian.jira;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public class JiraServerTests extends JavaRulesTestBaseCase {

    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.JIRA_SERVER;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("atlassian")
        .sourceKind("jira")
        .rulesFile("jira-server")
        .build();


    @Disabled // not reliable; seems to have different value via IntelliJ/AWS deployment and my
    // laptop's maven, which doesn't make any sense, given that binary deployed to AWS was built via
    // maven on my laptop - so something about Surefire/Junit runner used by maven??
    @Test
    void sha() {
        this.assertSha("7869e465607b7a00b4bd75a832a9ed1f811ce7f2");
    }

    @Test
    @Override
    @Disabled
    public void yamlLength() {
        // Do nothing, as response schema is bigger than we allow for advanced parameters
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "latest"})
    void server_issues_by_jql_v2(String version) {
        String jsonString = asJson("server_issues_by_jql_v2.json");

        String endpoint = String.format("https://myjiraserver.com/rest/api/%s/search?jql=something&startAt=50", version);

        Collection<String> PII = Arrays.asList("JIRAUSER10000", "fake@contoso.com", "fake");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "JIRAUSER10000");
        assertPseudonymized(sanitized, "fake@contoso.com");
        assertRedacted(sanitized,
                "fake", // display name
                "https://..." //photo url placeholders
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "latest"})
    void server_issue_comments_v2(String version) {
        String jsonString = asJson("server_issue_comment_v2.json");

        String endpoint = String.format("https://myjiraserver.com/rest/api/%s/issue/fake/comment?&startAt=50", version);

        Collection<String> PII = Arrays.asList("JIRAUSER10000", "fake@contoso.com", "fake");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "JIRAUSER10000");
        assertPseudonymized(sanitized, "fake@contoso.com");
        assertRedacted(sanitized,
                "Fake", // display name
                "https://..." //photo url placeholders
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "latest"})
    void server_issue_worklog_v2(String version) {
        String jsonString = asJson("server_issue_worklog_v2.json");

        String endpoint = String.format("https://myjiraserver.com/rest/api/%s/issue/fake/worklog?&startAt=50", version);

        Collection<String> PII = Arrays.asList("JIRAUSER10000", "fake@contoso.com", "fake");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "JIRAUSER10000");
        assertPseudonymized(sanitized, "fake@contoso.com");
        assertRedacted(sanitized,
                "Fake", // display name
                "https://..." //photo url placeholders
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "latest"})
    void issue(String version) {
        String jsonString = asJson("server_issue.json");

        String endpoint = String.format("https://myjiraserver.com/rest/api/%s/issue/ISSUE&startAt=50", version);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "JIRAUSER10000");
        assertPseudonymized(sanitized, "fake@contoso.com");
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
                InvocationExample.of("https://myjiraserver.com/rest/api/2/issue/ISSUE&startAt=50", "server_issue.json"),
                InvocationExample.of("https://myjiraserver.com/rest/api/2/search?jql=something&startAt=50", "server_issues_by_jql_v2.json"),
                InvocationExample.of("https://myjiraserver.com/rest/api/latest/search?jql=something&startAt=50", "server_issues_by_jql_v2.json"),
                InvocationExample.of("https://myjiraserver.com/rest/api/2/search?startAt=0&maxResults=50&jql=updated%20%3E%3D%20'2021/12/27%2008:00'%20AND%20updated%20%3C%20'2023/08/09%2017:48'%20ORDER%20BY%20updated%20DESC&fields=*all,-comment,-worklog",
                        "server_issues_by_jql_v2.json"),
                InvocationExample.of("https://myjiraserver.com/rest/api/2/issue/fake/comment?&startAt=50", "server_issue_comment_v2.json"),
                InvocationExample.of("https://myjiraserver.com/rest/api/latest/issue/fake/comment?&startAt=50", "server_issue_comment_v2.json"),
                InvocationExample.of("https://myjiraserver.com/rest/api/2/issue/fake/worklog?&startAt=50", "server_issue_worklog_v2.json"),
                InvocationExample.of("https://myjiraserver.com/rest/api/latest/issue/fake/worklog?&startAt=50", "server_issue_worklog_v2.json")
        );
    }
}
