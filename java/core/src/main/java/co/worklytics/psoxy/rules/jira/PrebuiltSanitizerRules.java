package co.worklytics.psoxy.rules.jira;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;

import java.util.List;
import java.util.stream.Collectors;

public class PrebuiltSanitizerRules {

    private static final List<String> commonAllowedQueryParameters = Lists.newArrayList(
            "startAt",
            "maxResults"
    );

    private static final List<String> issuesAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
                    Lists.newArrayList("jql",
                            "fields").stream())
            .collect(Collectors.toList());

    private static final List<String> groupMemberAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
                    Lists.newArrayList("groupId").stream())
            .collect(Collectors.toList());

    static final Endpoint ISSUES = Endpoint.builder()
            .pathRegex("^rest/api/3/search?[?]?[^/]*")
            .allowedQueryParams(issuesAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.issues[*].self")
                    .jsonPath("$.issues[*]..self")
                    .jsonPath("$.issues[*]..description")
                    .jsonPath("$.issues[*]..iconUrl")
                    .jsonPath("$.issues[*]..name")
                    .jsonPath("$.issues[*]..avatarUrls")
                    .jsonPath("$.issues[*]..displayName")
                    .jsonPath("$..displayName")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.issues[*]..accountId")
                    .jsonPath("$.issues[*]..emailAddress")
                    .build())
            .build();

    static final Endpoint ISSUE_CHANGELOG = Endpoint.builder()
            .pathRegex("^rest/api/3/issue/[^/]*/changelog?[?]?[^/]*")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.values[*].author.self")
                    .jsonPath("$.values[*].author.avatarUrls")
                    .jsonPath("$.values[*].author.displayName")
                    .jsonPath("$.values[*].items[*].from")
                    .jsonPath("$.values[*].items[*].to")
                    .jsonPath("$.values[*].items[*].fromString")
                    .jsonPath("$.values[*].items[*].toString")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.values[*].author.accountId")
                    .jsonPath("$.values[*].author.emailAddress")
                    .build())
            .build();

    static final Endpoint ISSUE_COMMENT = Endpoint.builder()
            .pathRegex("^rest/api/3/issue/[^/]*/comment?[?]?[^/]*")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.comments[*].(author|updateAuthor).self")
                    .jsonPath("$.comments[*].(author|updateAuthor).avatarUrls")
                    .jsonPath("$.comments[*].(author|updateAuthor).displayName")
                    .jsonPath("$.comments[*].body.content[*].text")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.comments[*].(author|updateAuthor).accountId")
                    .jsonPath("$.comments[*].(author|updateAuthor).emailAddress")
                    .build())
            .build();

    static final Endpoint ISSUE_WORKLOG = Endpoint.builder()
            .pathRegex("^rest/api/3/issue/[^/]*/worklog?[?]?[^/]*")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.worklogs[*].(author|updateAuthor).self")
                    .jsonPath("$.worklogs[*].(author|updateAuthor).avatarUrls")
                    .jsonPath("$.worklogs[*].(author|updateAuthor).displayName")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.worklogs[*].(author|updateAuthor).accountId")
                    .jsonPath("$.worklogs[*].(author|updateAuthor).emailAddress")
                    .build())
            .build();

    static final Endpoint USERS = Endpoint.builder()
            .pathRegex("^rest/api/3/user?[?]?[^/]*")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$..self")
                    .jsonPath("$..avatarUrls")
                    .jsonPath("$..displayName")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..accountId")
                    .jsonPath("$..emailAddress")
                    .build())
            .build();

    static final Endpoint GROUP_BULK = Endpoint.builder()
            .pathRegex("^rest/api/3/group/bulk?[?]?[^/]*")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$..name")
                    .build())
            .build();

    static final Endpoint GROUP_MEMBER = Endpoint.builder()
            .pathRegex("^rest/api/3/group/member?[?]?[^/]*")
            .allowedQueryParams(groupMemberAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.values[*].self")
                    .jsonPath("$.values[*].avatarUrls")
                    .jsonPath("$.values[*].displayName")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.values[*].accountId")
                    .jsonPath("$.values[*].emailAddress")
                    .build())
            .build();

    public static final RESTRules JIRA = Rules2.builder()
            .endpoint(GROUP_BULK)
            .endpoint(ISSUES)
            .endpoint(ISSUE_CHANGELOG)
            .endpoint(ISSUE_COMMENT)
            .endpoint(ISSUE_WORKLOG)
            .endpoint(USERS)
            .build();
}