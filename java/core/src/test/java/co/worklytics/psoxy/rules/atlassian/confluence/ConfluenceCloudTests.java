package co.worklytics.psoxy.rules.atlassian.confluence;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

@Getter
public class ConfluenceCloudTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.CONFLUENCE;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("atlassian")
        .sourceKind("confluence")
        .rulesFile("confluence")
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

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://api.atlassian.com/oauth/token/accessible-resources", "accessible_resources.json"),

            // Groups (v1)
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/rest/api/group?limit=100",
                "groups.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/rest/api/group?limit=5&start=5",
                "groups.json"
            ),
            // Group members (v1)
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/rest/api/group/35e417ad-bcb1-45fe-9be0-959239a84327/membersByGroupId?limit=100",
                "group_members.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/rest/api/group/35e417ad-bcb1-45fe-9be0-959239a84327/membersByGroupId?limit=100&start=100",
                "group_members.json"
            ),
            // Spaces (v2)
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/spaces?limit=100",
                "spaces.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/spaces?limit=100&cursor=some-cursor-value",
                "spaces.json"
            ),
            // Content search (v1)
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/rest/api/content/search?cql=lastmodified%3E%3D2025-08-01+AND+lastmodified%3C%3D2025-09-29+AND+space%3D%22~608a9b555426330072f9867d%22&limit=10",
                "content_search.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/rest/api/content/search?cursor=_f_MTA%3D_sa_WyJcdDI5NDkyMSA6X0MmdTknU1pvWmJDYnNsdF0haCBjYyJd&expand=body.atlas_doc_format%2Cancestors%2Cversion%2Chistory%2Chistory.previousVersion&includeArchivedSpaces=true&limit=10&start=10&cql=lastmodified%3E%3D2025-08-01+AND+lastmodified%3C%3D2025-09-29+AND+space%3D%22~608a9b555426330072f9867d%22",
                "content_search.json"
            ),
            // Versions (v2)
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/attachments/att66076/versions?limit=10",
                "attachment_versions.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/attachments/att66076/versions?limit=10&cursor=some-cursor-value",
                "attachment_versions.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/blogposts/1234/versions?limit=10",
                "blogpost_versions.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/blogposts/1234/versions?limit=10&cursor=some-cursor-value",
                "blogpost_versions.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/custom-content/1234/versions?limit=10",
                "custom_content_versions.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/custom-content/1234/versions?limit=10&cursor=some-cursor-value",
                "custom_content_versions.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/comments/1234/versions?limit=10",
                "comment_versions.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/comments/1234/versions?limit=10&cursor=some-cursor-value",
                "comment_versions.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/pages/1234/versions?limit=10",
                "page_versions.json"
            ),
            InvocationExample.of(
                "https://api.atlassian.com/ex/confluence/company-team/wiki/api/v2/pages/1234/versions?limit=10&cursor=some-cursor-value",
                "page_versions.json"
            )
        );
    }
}
