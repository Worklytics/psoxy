package co.worklytics.psoxy.rules.atlassian.jira;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Note about V2 and V3 versions:
 * At the moment of implementing current rules, <a href="https://developer.atlassian.com/cloud/jira/platform/rest/v2/intro/#version">docs</a>  says the following:
 * "
 * The latest version of the Jira Cloud platform REST API is version 3, which is in beta.
 * Version 2 and 3 of the API offer the same collection of operations.
 * However, version 3 provides support for the Atlassian Document Format (ADF). The ADF features in version 3 are under development."
 * Main difference is in "Comments" between both versions, present in comments entity ifself, issues and worklogs. In v2, comments include the body as a plain text -which is redacted
 * as part of the rules-. In v3, apart from plain text it included the "Atlassian Document Format" which is an object where it includes
 * information about comment, such id of the users mentioned -in V3, these are pseudonymized-
 */
public class PrebuiltSanitizerRules {

    private static final List<String> commonAllowedQueryParameters = Lists.newArrayList(
        "startAt",
        "maxResults"
    );

    private static final List<String> issuesAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            Lists.newArrayList("jql",
                "fields").stream())
        .collect(Collectors.toList());

    private static final List<String> issueAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            Lists.newArrayList("expand", "fields").stream())
        .collect(Collectors.toList());

    private static final List<String> groupMemberAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            Lists.newArrayList("groupId",
                "groupName",
                "includeInactiveUsers").stream())
        .collect(Collectors.toList());

    private static final List<String> projectServerAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            Lists.newArrayList("expand",
                    "includeArchived")
                .stream())
        .collect(Collectors.toList());

    private static final List<String> userServerAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            Lists.newArrayList("username",
                    "includeActive",
                    "includeInactive")
                .stream())
        .collect(Collectors.toList());

    static final Endpoint ISSUE_SEARCH_V2 = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/2/search")
        .allowedQueryParams(issuesAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.issues[*]..description")
            .jsonPath("$.issues[*]..iconUrl")
            .jsonPath("$.issues[*]..name")
            .jsonPath("$.issues[*]..avatarUrls")
            .jsonPath("$.issues[*].fields..self")
            .jsonPath("$.issues[*]..displayName")
            .jsonPath("$.issues[*]..body")
            .jsonPath("$.issues[*]..comment")
            .jsonPath("$..displayName")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.issues[*]..accountId")
            .jsonPath("$.issues[*]..emailAddress")
            .build())
        .responseSchema(jsonSchemaForQueryResult(true))
        .build();

    static final Endpoint ISSUE_SEARCH_V3 = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/3/search/jql")
        .allowedQueryParams(issuesAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.issues[*]..description")
            .jsonPath("$.issues[*]..iconUrl")
            .jsonPath("$.issues[*]..name")
            .jsonPath("$.issues[*]..avatarUrls")
            .jsonPath("$.issues[*].fields..self")
            .jsonPath("$.issues[*]..displayName")
            .jsonPath("$.issues[*]..comment")
            .jsonPath("$..displayName")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.issues[*]..accountId")
            .jsonPath("$.issues[*]..emailAddress")
            .jsonPath("$.issues[*]..body..id")
            .build())
        .responseSchema(jsonSchemaForQueryResult(true))
        .build();

    static final Endpoint ISSUE_V2 = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/2/issue/{issueId}")
        .allowedQueryParams(issuesAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..description")
            .jsonPath("$..iconUrl")
            .jsonPath("$..name")
            .jsonPath("$..avatarUrls")
            .jsonPath("$.fields..self")
            .jsonPath("$..displayName")
            .jsonPath("$..body")
            .jsonPath("$..comment")
            .jsonPath("$..attachment[*]..filename")
            .jsonPath("$..attachment[*]..content")
            .jsonPath("$..summary")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..accountId")
            .jsonPath("$..emailAddress")
            .build())
        .responseSchema(jsonSchemaForIssue(true))
        .build();

    static final Endpoint ISSUE_V3 = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/3/issue/{issueId}")
        .allowedQueryParams(issuesAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..description")
            .jsonPath("$..iconUrl")
            .jsonPath("$..name")
            .jsonPath("$..avatarUrls")
            .jsonPath("$.fields..self")
            .jsonPath("$..displayName")
            .jsonPath("$..comment")
            .jsonPath("$..attachment[*]..filename")
            .jsonPath("$..attachment[*]..content")
            .jsonPath("$..summary")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..accountId")
            .jsonPath("$..emailAddress")
            .jsonPath("$..body..id")
            .build())
        .responseSchema(jsonSchemaForIssue(true))
        .build();

    static final Endpoint SERVER_ISSUE_V2 = Endpoint.builder()
        .pathTemplate("/rest/api/{apiVersion}/issue/{issueId}")
        .allowedQueryParams(issueAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..self")
            .jsonPath("$..description")
            .jsonPath("$..iconUrl")
            .jsonPath("$..name")
            .jsonPath("$..avatarUrls")
            .jsonPath("$..displayName")
            .jsonPath("$..name")
            .jsonPath("$..body")
            .jsonPath("$..comment")
            .jsonPath("$..displayName")
            .jsonPath("$..from")
            .jsonPath("$..to")
            .jsonPath("$..fromString")
            .jsonPath("$..toString")
            .jsonPath("$..attachment[*]..filename")
            .jsonPath("$..attachment[*]..content")
            .jsonPath("$..summary")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..author..key")
            .jsonPath("$..author..emailAddress")
            .jsonPath("$..creator..key")
            .jsonPath("$..creator..emailAddress")
            .jsonPath("$..reporter..key")
            .jsonPath("$..reporter..emailAddress")
            .jsonPath("$..updateAuthor..key")
            .jsonPath("$..updateAuthor..emailAddress")
            .build())
        .responseSchema(jsonSchemaForIssue(false))
        .build();

    static final Endpoint SERVER_ISSUE_SEARCH_V2 = Endpoint.builder()
        .pathTemplate("/rest/api/{apiVersion}/search")
        .allowedQueryParams(issuesAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.issues[*]..description")
            .jsonPath("$.issues[*]..iconUrl")
            .jsonPath("$.issues[*]..name")
            .jsonPath("$.issues[*]..avatarUrls")
            .jsonPath("$.issues[*]..displayName")
            .jsonPath("$.issues[*]..name")
            .jsonPath("$.issues[*]..body")
            .jsonPath("$.issues[*]..comment")
            .jsonPath("$..displayName")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.issues[*]..key")
            .jsonPath("$.issues[*]..emailAddress")
            .build())
        .responseSchema(jsonSchemaForQueryResult(false))
        .build();

    static final Endpoint ISSUE_CHANGELOG = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/{apiVersion}/issue/{issueId}/changelog")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.values[*]..avatarUrls")
            .jsonPath("$.values[*]..self")
            .jsonPath("$.values[*]..displayName")
            .jsonPath("$.values[*].items[*].from")
            .jsonPath("$.values[*].items[*].to")
            .jsonPath("$.values[*].items[*].fromString")
            .jsonPath("$.values[*].items[*].toString")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.values[*]..accountId")
            .jsonPath("$.values[*]..emailAddress")
            .jsonPath("$.values[*]..tmpFromAccountId")
            .jsonPath("$.values[*]..tmpToAccountId")
            .build())
        .build();

    static final Endpoint ISSUE_COMMENT_V2 = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/2/issue/{issueId}/comment")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.comments[*]..avatarUrls")
            .jsonPath("$.comments[*].author..self")
            .jsonPath("$.comments[*].updateAuthor..self")
            .jsonPath("$.comments[*]..displayName")
            .jsonPath("$.comments[*]..text")
            .jsonPath("$.comments[*]..body")
            .jsonPath("$.comments[*]..renderedBody")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.comments[*]..accountId")
            .jsonPath("$.comments[*]..emailAddress")
            .build())
        .build();

    static final Endpoint ISSUE_COMMENT_V3 = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/3/issue/{issueId}/comment")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.comments[*]..avatarUrls")
            .jsonPath("$.comments[*].author..self")
            .jsonPath("$.comments[*].updateAuthor..self")
            .jsonPath("$.comments[*]..displayName")
            .jsonPath("$.comments[*]..text")
            .jsonPath("$.comments[*]..renderedBody")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.comments[*]..accountId")
            .jsonPath("$.comments[*].body..id")
            .jsonPath("$.comments[*]..emailAddress")
            .build())
        .build();

    static final Endpoint SERVER_ISSUE_COMMENT_V2 = Endpoint.builder()
        .pathTemplate("/rest/api/{apiVersion}/issue/{issueId}/comment")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.comments[*]..avatarUrls")
            .jsonPath("$.comments[*].author..self")
            .jsonPath("$.comments[*].updateAuthor..self")
            .jsonPath("$.comments[*]..displayName")
            .jsonPath("$.comments[*]..name")
            .jsonPath("$.comments[*]..text")
            .jsonPath("$.comments[*]..body")
            .jsonPath("$.comments[*]..renderedBody")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.comments[*]..key")
            .jsonPath("$.comments[*]..emailAddress")
            .build())
        .build();


    static final Endpoint ISSUE_WORKLOG_V2 = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/2/issue/{issueId}/worklog")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.worklogs[*]..avatarUrls")
            .jsonPath("$.worklogs[*].author..self")
            .jsonPath("$.worklogs[*].updateAuthor..self")
            .jsonPath("$.worklogs[*]..displayName")
            .jsonPath("$.worklogs[*]..comment")
            .jsonPath("$.worklogs[*]..summary")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.worklogs[*]..accountId")
            .jsonPath("$.worklogs[*]..emailAddress")
            .build())
        .build();

    static final Endpoint ISSUE_WORKLOG_V3 = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/3/issue/{issueId}/worklog")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.worklogs[*]..avatarUrls")
            .jsonPath("$.worklogs[*].author..self")
            .jsonPath("$.worklogs[*].updateAuthor..self")
            .jsonPath("$.worklogs[*]..displayName")
            .jsonPath("$.worklogs[*]..text")
            .jsonPath("$.worklogs[*]..summary")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.worklogs[*]..accountId")
            .jsonPath("$.worklogs[*]..emailAddress")
            .jsonPath("$.worklogs[*]..comment..id")
            .build())
        .build();

    static final Endpoint SERVER_ISSUE_WORKLOG_V2 = Endpoint.builder()
        .pathTemplate("/rest/api/{apiVersion}/issue/{issueId}/worklog")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.worklogs[*]..avatarUrls")
            .jsonPath("$.worklogs[*].author..self")
            .jsonPath("$.worklogs[*].updateAuthor..self")
            .jsonPath("$.worklogs[*]..displayName")
            .jsonPath("$.worklogs[*]..name")
            .jsonPath("$.worklogs[*]..comment")
            .jsonPath("$.worklogs[*]..summary")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.worklogs[*]..key")
            .jsonPath("$.worklogs[*]..emailAddress")
            .build())
        .build();

    static final Endpoint SERVER_USERS = Endpoint.builder()
        .pathTemplate("/rest/api/{apiVersion}/user/search")
        .allowedQueryParams(userServerAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..self")
            .jsonPath("$..avatarUrls")
            .jsonPath("$..displayName")
            .jsonPath("$..name")
            .jsonPath("$..attachment[*]..filename")
            .jsonPath("$..attachment[*]..content")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..key")
            .jsonPath("$..emailAddress")
            .build())
        .build();

    static final Endpoint USERS = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/{apiVersion}/users")
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
        .pathTemplate("/ex/jira/{cloudId}/rest/api/{apiVersion}/group/bulk")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..name")
            .build())
        .build();

    static final Endpoint GROUP_MEMBER = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/{apiVersion}/group/member")
        .allowedQueryParams(groupMemberAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.values[*].self")
            .jsonPath("$.values[*].avatarUrls")
            .jsonPath("$.values[*].displayName")
            .jsonPath("$.values[*].name")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.values[*].accountId")
            .jsonPath("$.values[*].emailAddress")
            .build())
        .build();

    static final Endpoint PROJECTS = Endpoint.builder()
        .pathTemplate("/ex/jira/{cloudId}/rest/api/{apiVersion}/project/search")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$.values[*]..avatarUrls")
            .jsonPath("$.values[*]..self")
            .jsonPath("$.values[*]..displayName")
            .jsonPath("$.values[*]..leadUserName")
            .jsonPath("$.values[*]..description")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.values[*]..accountId")
            .jsonPath("$.values[*]..emailAddress")
            // An email associated with the project
            .jsonPath("$.values[*]..email")
            .jsonPath("$.values[*]..leadAccountId")
            .build())
        .build();

    static final Endpoint ACCESSIBLE_RESOURCES = Endpoint.builder()
        .pathTemplate("/oauth/token/accessible-resources")
        .transform(Transform.Redact.builder()
            .jsonPath("$..name")
            .jsonPath("$..scopes")
            .jsonPath("$..avatarUrl")
            .build())
        .build();

    static final Endpoint SERVER_PROJECTS = Endpoint.builder()
        .pathTemplate("/rest/api/{apiVersion}/project")
        .allowedQueryParams(projectServerAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..avatarUrls")
            .jsonPath("$..self")
            .jsonPath("$..displayName")
            .jsonPath("$..leadUserName")
            .jsonPath("$..description")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..lead..key")
            .jsonPath("$..lead..emailAddress")
            .jsonPath("$..assignee..key")
            .jsonPath("$..assignee..emailAddress")
            .jsonPath("$..realAssignee..key")
            .jsonPath("$..realAssignee..emailAddress")
            .jsonPath("$..user..key")
            .jsonPath("$..user..emailAddress")
            .build())
        .build();

    @VisibleForTesting
    static final RESTRules JIRA_CLOUD = Rules2.builder()
        .endpoint(GROUP_BULK)
        .endpoint(GROUP_MEMBER)
        .endpoint(ISSUE_SEARCH_V3)
        .endpoint(ISSUE_CHANGELOG)
        .endpoint(ISSUE_COMMENT_V3)
        .endpoint(ISSUE_WORKLOG_V3)
        .endpoint(ISSUE_V3)
        .endpoint(USERS)
        .endpoint(PROJECTS)
        .endpoint(ACCESSIBLE_RESOURCES)
        .build();

    @VisibleForTesting
    static final RESTRules JIRA_SERVER = Rules2.builder()
        .endpoint(SERVER_ISSUE_SEARCH_V2)
        .endpoint(SERVER_ISSUE_COMMENT_V2)
        .endpoint(SERVER_ISSUE_WORKLOG_V2)
        .endpoint(SERVER_ISSUE_V2)
        .endpoint(SERVER_PROJECTS)
        .endpoint(SERVER_USERS)
        .build();

    public static final Map<String, RESTRules> RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("jira-server", JIRA_SERVER)
            .put("jira-cloud", JIRA_CLOUD)
            .build();

    private static JsonSchemaFilter jsonSchemaForQueryResult(boolean isCloudVersion) {
        return JsonSchemaFilter.builder()
            .type("object")
            // Using LinkedHashMap to keep the order to support same
            // YAML serialization result
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                put("startAt", JsonSchemaFilter.<String, RESTRules>builder()
                    .type("integer")
                    .build());
                put("maxResults", JsonSchemaFilter.builder()
                    .type("integer")
                    .build());
                put("total", JsonSchemaFilter.builder()
                    .type("integer")
                    .build());
                put("issues", JsonSchemaFilter.builder()
                    .type("array")
                    .items(jsonSchemaForIssue(isCloudVersion))
                    .build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForIssue(boolean isCloudVersion) {
        return JsonSchemaFilter.builder()
            .type("object")
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                put("id", JsonSchemaFilter.builder().type("string").build());
                put("self", JsonSchemaFilter.builder().type("string").build());
                put("fields", jsonSchemaForIssueFields(isCloudVersion));
                put("changelog",
                    JsonSchemaFilter.builder()
                        .type("object")
                        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                            put("histories", JsonSchemaFilter.builder().type("array").items(jsonSchemaForIssueChangelog(isCloudVersion)).build());
                        }}).build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForIssueFields(boolean isCloudVersion) {
        return JsonSchemaFilter.builder()
            .type("object")
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                // NOTE: in response, fields appears "as-is" in format. Some of them in lowercase, others in camel case...
                put("statuscategorychangedate", JsonSchemaFilter.builder().type("string").build());
                put("issuetype", jsonSchemaForIssueType());
                put("parent", jsonSchemaForIssueParent());
                put("timespent", JsonSchemaFilter.builder().type("integer").build());
                // In doc appears as "watchers", but in actual response is "watches"
                put("watches", JsonSchemaFilter.builder()
                    .type("object")
                    .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                        put("isWatching", JsonSchemaFilter.builder().type("boolean").build());
                        put("watchCount", JsonSchemaFilter.builder().type("integer").build());
                        put("watchers", JsonSchemaFilter.builder().type("array").items(jsonSchemaForUser(isCloudVersion)).build());
                    }}).build());
                put("attachment", JsonSchemaFilter.builder()
                    .type("array")
                    .items(JsonSchemaFilter.builder()
                        .type("object")
                        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                            put("id", JsonSchemaFilter.builder().type("string").build());
                            put("author", jsonSchemaForUser(isCloudVersion));
                            put("created", JsonSchemaFilter.builder().type("string").build());
                            put("size", JsonSchemaFilter.builder().type("integer").build());
                        }}).build()).build());
                // In docs appears as "sub-tasks", but in actual response is "subtasks"
                put("sub-tasks", jsonSchemaForLinks());
                put("project", JsonSchemaFilter.builder()
                    .type("object")
                    .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                        put("id", JsonSchemaFilter.builder().type("string").build());
                        put("projectCategory", JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                                put("id", JsonSchemaFilter.builder().type("string").build());
                            }}).build());
                        put("projectTypeKey", JsonSchemaFilter.builder().type("string").build());
                        put("simplified", JsonSchemaFilter.builder().type("boolean").build());
                        put("style", JsonSchemaFilter.builder().type("string").build());
                        put("insight", JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                put("totalIssueCount", JsonSchemaFilter.builder().type("integer").build());
                                put("lastIssueUpdateTime", JsonSchemaFilter.builder().type("string").build());
                            }}).build());
                    }}).build());
                put("comment", JsonSchemaFilter.builder()
                    .type("array")
                    .items(JsonSchemaFilter.builder()
                        .type("object")
                        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                            put("id", JsonSchemaFilter.builder().type("string").build());
                            put("author", jsonSchemaForUser(isCloudVersion));
                            put("updateAuthor", jsonSchemaForUser(isCloudVersion));
                            put("created", JsonSchemaFilter.builder().type("string").build());
                            put("updated", JsonSchemaFilter.builder().type("string").build());
                            put("visibility", jsonSchemaForVisibility());
                        }}).build())
                    .build());
                put("issuelinks", jsonSchemaForLinks());
                put("worklog", JsonSchemaFilter.builder()
                    .type("object")
                    .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                        put("worklogs", JsonSchemaFilter.builder().type("array").items(jsonSchemaForIssueWorklog(isCloudVersion)).build());
                    }}).build());
                put("updated", JsonSchemaFilter.builder().type("integer").build());
                put("timeTracking", JsonSchemaFilter.builder()
                    .type("object")
                    .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                        put("originalEstimate", JsonSchemaFilter.builder().type("string").build());
                        put("remainingEstimate", JsonSchemaFilter.builder().type("string").build());
                        put("timeSpent", JsonSchemaFilter.builder().type("string").build());
                        put("originalEstimateSeconds", JsonSchemaFilter.builder().type("integer").build());
                        put("remainingEstimateSeconds", JsonSchemaFilter.builder().type("integer").build());
                        put("timeSpentSeconds", JsonSchemaFilter.builder().type("integer").build());
                    }}).build());

                put("created", JsonSchemaFilter.builder().type("string").build());
                put("resolutiondate", JsonSchemaFilter.builder().type("string").build());
                put("lastViewed", JsonSchemaFilter.builder().type("string").build());
                put("assignee", jsonSchemaForUser(isCloudVersion));
                put("status", jsonSchemaForIssueStatus());
                put("creator", jsonSchemaForUser(isCloudVersion));
                put("reporter", jsonSchemaForUser(isCloudVersion));

                put("aggregateprogress", jsonSchemaForProgressInformation());

                put("duedate", JsonSchemaFilter.builder().type("string").build());

                put("progress", jsonSchemaForProgressInformation());

                put("votes", JsonSchemaFilter.builder()
                    .type("object")
                    .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                        put("votes", JsonSchemaFilter.builder().type("integer").build());
                        put("hasVoted", JsonSchemaFilter.builder().type("boolean").build());
                    }}).build());

            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForIssueWorklog(boolean isCloudVersion) {
        return JsonSchemaFilter.builder()
            .type("object")
            // Using LinkedHashMap to keep the order to support same
            // YAML serialization result
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                put("issueId", JsonSchemaFilter.builder().type("string").build());
                put("id", JsonSchemaFilter.builder().type("string").build());
                put("author", jsonSchemaForUser(isCloudVersion));
                put("created", JsonSchemaFilter.builder().type("string").build());
                put("updateAuthor", jsonSchemaForUser(isCloudVersion));
                put("updated", JsonSchemaFilter.builder().type("string").build());
                put("visibility", jsonSchemaForVisibility());
                put("started", JsonSchemaFilter.builder().type("string").build());
                put("timeSpent", JsonSchemaFilter.builder().type("string").build());
                put("timeSpentSeconds", JsonSchemaFilter.builder().type("integer").build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForIssueChangelog(boolean isCloudVersion) {
        return JsonSchemaFilter.builder()
            .type("object")
            // Using LinkedHashMap to keep the order to support same
            // YAML serialization result
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                put("id", JsonSchemaFilter.builder().type("string").build());
                put("author", jsonSchemaForUser(isCloudVersion));
                put("created", JsonSchemaFilter.builder().type("string").build());
                put("items", JsonSchemaFilter.builder()
                    .type("array")
                    .items(JsonSchemaFilter.builder()
                        .type("object")
                        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                            put("field", JsonSchemaFilter.builder().type("string").build());
                            put("fieldtype", JsonSchemaFilter.builder().type("string").build());
                        }})
                        .build())
                    .build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForIssuePriority() {
        return JsonSchemaFilter.builder()
            .type("object")
            // Using LinkedHashMap to keep the order to support same
            // YAML serialization result
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                put("id", JsonSchemaFilter.builder().type("string").build());
                put("name", JsonSchemaFilter.builder().type("string").build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForIssueType() {
        return JsonSchemaFilter.builder()
            .type("object")
            // Using LinkedHashMap to keep the order to support same
            // YAML serialization result
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                put("id", JsonSchemaFilter.builder().type("string").build());
                put("subtask", JsonSchemaFilter.builder().type("boolean").build());
                put("hierarchyLevel", JsonSchemaFilter.builder().type("integer").build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForIssueParent() {
        return JsonSchemaFilter.builder()
            .type("object")
            // Using LinkedHashMap to keep the order to support same
            // YAML serialization result
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility

                put("id", JsonSchemaFilter.builder().type("string").build());
                put("fields",
                    JsonSchemaFilter.builder()
                        .type("object")
                        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                            put("status", jsonSchemaForIssueStatus());
                            put("priority", jsonSchemaForIssuePriority());
                            put("issuetype", jsonSchemaForIssueType());
                        }})
                        .build()
                );

            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForIssueStatus() {
        return JsonSchemaFilter.builder()
            .type("object")
            // Using LinkedHashMap to keep the order to support same
            // YAML serialization result
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                put("id", JsonSchemaFilter.builder().type("string").build());
                put("name", JsonSchemaFilter.builder().type("string").build());
                put("statusCategory", JsonSchemaFilter.builder()
                    .type("object")
                    .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                        put("id", JsonSchemaFilter.builder().type("integer").build());
                        put("key", JsonSchemaFilter.builder().type("string").build());
                        put("colorName", JsonSchemaFilter.builder().type("string").build());
                        put("name", JsonSchemaFilter.builder().type("string").build());
                    }})
                    .build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForUser(boolean isCloudVersion) {
        return JsonSchemaFilter.builder()
            .type("object")
            // Using LinkedHashMap to keep the order to support same
            // YAML serialization result
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                put(isCloudVersion ? "accountId" : "key", JsonSchemaFilter.builder().type("string").build());
                put("accountType", JsonSchemaFilter.builder().type("string").build());
                put("emailAddress", JsonSchemaFilter.builder().type("string").build());
                put("active", JsonSchemaFilter.builder().type("boolean").build());
                put("timeZone", JsonSchemaFilter.builder().type("string").build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForLinks() {
        return JsonSchemaFilter.builder()
            .type("array")
            .items(JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("id", JsonSchemaFilter.builder().type("string").build());
                    put("type", JsonSchemaFilter.builder()
                        .type("object")
                        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                            put("id", JsonSchemaFilter.builder().type("string").build());
                            put("inward", JsonSchemaFilter.builder().type("string").build());
                            put("outward", JsonSchemaFilter.builder().type("string").build());
                        }}).build());
                    put("outwardIssue", JsonSchemaFilter.builder()
                        .type("object")
                        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                            put("id", JsonSchemaFilter.builder().type("string").build());
                        }}).build());
                    put("inwardIssue", JsonSchemaFilter.builder()
                        .type("object")
                        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                            put("id", JsonSchemaFilter.builder().type("string").build());
                        }}).build());
                }})
                .build())
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForVisibility() {
        return JsonSchemaFilter.builder()
            .type("object")
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                put("type", JsonSchemaFilter.builder().type("string").build());
                put("value", JsonSchemaFilter.builder().type("string").build());
                put("identifier", JsonSchemaFilter.builder().type("string").build());
            }}).build();
    }

    private static JsonSchemaFilter jsonSchemaForProgressInformation() {
        return JsonSchemaFilter.builder()
            .type("object")
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                put("progress", JsonSchemaFilter.builder().type("integer").build());
                put("total", JsonSchemaFilter.builder().type("integer").build());
            }}).build();
    }
}
