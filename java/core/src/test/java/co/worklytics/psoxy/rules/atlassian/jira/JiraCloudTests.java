package co.worklytics.psoxy.rules.atlassian.jira;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.atlassian.jira.PrebuiltSanitizerRules;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

@Getter
public class JiraCloudTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.JIRA_CLOUD;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("atlassian")
        .sourceKind("jira")
        .rulesFile("jira-cloud")
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

    @Test
    void users() {
        String jsonString = asJson("users.json");

        //no single-user case
        assertUrlBlocked("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/users?accountId=5b10ac8d82e05b22cc7d4ef5'");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/users?startAt=0&maxResults=25";

        Collection<String> PII = Arrays.asList("608a9b555426330072f9867d", "fake@contoso.com", "Fake");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "608a9b555426330072f9867d");
        assertPseudonymized(sanitized, "fake@contoso.com");
        assertRedacted(sanitized,
            "Fake", // display name
            "https://..." //photo url placeholders
        );

        //ensure we allow paging of users, and passing cloud id
        assertUrlWithQueryParamsBlocked("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/users");
    }

    @Test
    void groups_bulk() {
        String jsonString = asJson("groups.json");

        //no single-user case
        assertUrlBlocked("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/group/bulk?groupId=5b10ac8d82e05b22cc7d4ef5");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/group/bulk";

        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized, "jira-software-users", "administrators");
    }

    @Test
    void group_member() {
        String jsonString = asJson("group_member.json");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/group/member?groupId=5b10ac8d82e05b22cc7d4ef5";

        Collection<String> PII = Arrays.asList("5b10a2844c20165700ede21g", "mia@example.com", "Mia");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "5b10a2844c20165700ede21g");
        assertPseudonymized(sanitized, "mia@example.com");
        assertRedacted(sanitized,
            "Mia", // display name
            "some name", // name
            "https://..." //photo url placeholders
        );
    }

    @Test
    void issues_by_jql() {
        String jsonString = asJson("issues_by_jql.json");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/search/jql?jql=something";

        Collection<String> PII = Arrays.asList("712020:4891947c-7a8e-4889-b2cc-4064669804e1", "Bob Smith");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "712020:4891947c-7a8e-4889-b2cc-4064669804e1");
        assertRedacted(sanitized,
            "Bob Smith", // display name
            "https://..." //photo url placeholders
        );

        assertNotSanitized(sanitized, "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/issue/10712");
    }

    @Test
    void issue() {
        String jsonString = asJson("issue.json");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/issue/ISSUE";

        Collection<String> PII = Arrays.asList("608a9b555426330072f9867d", "fake@contoso.com", "Fake");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);
    }

    @Test
    void issue_changelog() {
        String jsonString = asJson("issue_changelog.json");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/issue/fake/changelog?&startAt=50";

        Collection<String> PII = Arrays.asList("608a9b555426330072f9867d", "fake@contoso.com", "Fake");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "608a9b555426330072f9867d");
        assertPseudonymized(sanitized, "fake@contoso.com");
        assertRedacted(sanitized,
            "Fake", // display name
            "https://..." //photo url placeholders
        );
    }

    @Test
    void issue_comments_v3() {
        String jsonString = asJson("issue_comment_v3.json");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/issue/fake/comment?&startAt=50";

        Collection<String> PII = Arrays.asList("608a9b555426330072f9867d", "fake@contoso.com", "Fake");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "608a9b555426330072f9867d");
        assertPseudonymized(sanitized, "fake@contoso.com");
        assertRedacted(sanitized,
            "Fake", // display name
            "https://..." //photo url placeholders
        );
    }

    @Test
    void issue_worklog_v3() {
        String jsonString = asJson("issue_worklog_v3.json");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/issue/fake/worklog?&startAt=50";

        Collection<String> PII = Arrays.asList("608a9b555426330072f9867d", "fake@contoso.com", "Fake");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "608a9b555426330072f9867d");
        assertPseudonymized(sanitized, "fake@contoso.com");
        assertRedacted(sanitized,
            "Fake", // display name
            "https://..." //photo url placeholders
        );
    }

    @Test
    void projects() {
        String jsonString = asJson("projects.json");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/project/search?startAt=0&maxResults=25";
        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized,
            "Fake", // display name
            "https://..." //photo url placeholders
        );

        //ensure we allow paging of users, and passing cloud id
        assertUrlWithQueryParamsBlocked("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/project/search");
    }

    @Test
    void accessible_resources() {
        String jsonString = asJson("accessible_resources.json");

        String endpoint = "https://api.atlassian.com/oauth/token/accessible-resources";
        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized,
            "scopes", // display name
            ".png" //photo url placeholders
        );
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://api.atlassian.com/oauth/token/accessible-resources", "accessible_resources.json"),
            InvocationExample.of("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/users?startAt=0&maxResults=25", "users.json"),
            InvocationExample.of("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/group/bulk", "groups.json"),
            InvocationExample.of("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/group/member?groupId=5b10ac8d82e05b22cc7d4ef5", "group_member.json"),
            InvocationExample.of("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/search/jql?jql=updated >= '2022/03/19 13:37' AND updated < '2025/06/17 14:37' ORDER BY updated DESC&fields=*all,-comment,-worklog&nextPageToken=token", "issues_by_jql.json"),
            InvocationExample.of("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/issue/fake/changelog?&startAt=50", "issue_changelog.json"),
            InvocationExample.of("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/issue/fake/comment?&startAt=50", "issue_comment_v3.json"),
            InvocationExample.of("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/issue/fake/worklog?&startAt=50", "issue_worklog_v3.json"),
            InvocationExample.of("https://api.atlassian.com/ex/jira/e9224a3c-0479-4ebc-9e5f-340c81d142c1/rest/api/3/issue/247393/worklog?startAt=0&maxResults=50", "issue_worklog_v3.json"));
    }
}
