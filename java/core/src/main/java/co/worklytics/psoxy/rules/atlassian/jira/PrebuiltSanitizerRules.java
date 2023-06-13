package co.worklytics.psoxy.rules.atlassian.jira;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilterUtils;
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
                    Lists.newArrayList("expand").stream())
            .collect(Collectors.toList());

    private static final List<String> groupMemberAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
                    Lists.newArrayList("groupId",
                            "groupName",
                            "includeInactiveUsers").stream())
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
            .pathTemplate("/ex/jira/{cloudId}/rest/api/3/search")
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
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..author..key")
                    .jsonPath("$..author..emailAddress")
                    .jsonPath("$..updateAuthor..key")
                    .jsonPath("$..updateAuthor..emailAddress")
                    .build())
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
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.worklogs[*]..key")
                    .jsonPath("$.worklogs[*]..emailAddress")
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

    @VisibleForTesting
    static final RESTRules JIRA_CLOUD = Rules2.builder()
            .endpoint(GROUP_BULK)
            .endpoint(GROUP_MEMBER)
            .endpoint(ISSUE_SEARCH_V2)
            .endpoint(ISSUE_SEARCH_V3)
            .endpoint(ISSUE_CHANGELOG)
            .endpoint(ISSUE_COMMENT_V2)
            .endpoint(ISSUE_COMMENT_V3)
            .endpoint(ISSUE_WORKLOG_V2)
            .endpoint(ISSUE_WORKLOG_V3)
            .endpoint(USERS)
            .endpoint(PROJECTS)
            .build();

    @VisibleForTesting
    static final RESTRules JIRA_SERVER = Rules2.builder()
            .endpoint(SERVER_ISSUE_SEARCH_V2)
            .endpoint(SERVER_ISSUE_COMMENT_V2)
            .endpoint(SERVER_ISSUE_WORKLOG_V2)
            .endpoint(SERVER_ISSUE_V2)
            .build();

    public static final Map<String, RESTRules> RULES_MAP =
            ImmutableMap.<String, RESTRules>builder()
                    .put("jira-server", JIRA_SERVER)
                    .put("jira-cloud", JIRA_CLOUD)
                    .build();

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForQueryResult(boolean isCloudVersion) {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("startAt", JsonSchemaFilterUtils.JsonSchemaFilter.<String, RESTRules>builder()
                            .type("integer")
                            .build());
                    put("maxResults", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("integer")
                            .build());
                    put("total", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("integer")
                            .build());
                    put("issues", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("array")
                            .items(jsonSchemaForIssue(isCloudVersion))
                            .build());
                }})
                .build();
    }


    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForIssue(boolean isCloudVersion) {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("self", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("fields", jsonSchemaForIssueFields(isCloudVersion));
                }})
                .build();
    }

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForIssueFields(boolean isCloudVersion) {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    // NOTE: in response, fields appears "as-is" in format. Some of them in lowercase, others in camel case...
                    put("statuscategorychangedate", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("issuetype", jsonSchemaForIssueType());
                    put("parent", jsonSchemaForIssueParent());
                    put("timespent", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    // In doc appears as "watchers", but in actual response is "watches"
                    put("watches", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                put("isWatching", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("boolean").build());
                                put("watchCount", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                                put("watchers", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("array").items(jsonSchemaForUser(isCloudVersion)).build());
                            }}).build());
                    put("attachment", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("array")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                                put("author", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("object").items(jsonSchemaForUser(isCloudVersion)).build());
                                put("created", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("size", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());

                            }}).build());
                    // In docs appears as "sub-tasks", but in actual response is "subtasks"
                    put("sub-tasks", jsonSchemaForLinks());
                    put("project", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{
                                put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("projectCategory", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                                        .type("object")
                                        .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{
                                            put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                        }}).build());
                                put("projectTypeKey", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("simplified", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("boolean").build());
                                put("style", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("insight", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                                        .type("object")
                                        .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                            put("totalIssueCount", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                                            put("lastIssueUpdateTime", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                        }}).build());
                            }}).build());
                    put("comment", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("array")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{
                                put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("author", jsonSchemaForUser(isCloudVersion));
                                put("updateAuthor", jsonSchemaForUser(isCloudVersion));
                                put("created", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("updated", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("visibility", jsonSchemaForVisibility());
                            }}).build());
                    put("issuelinks", jsonSchemaForLinks());
                    put("worklog", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("array")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{
                                put("issueId", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("author", jsonSchemaForUser(isCloudVersion));
                                put("updateAuthor", jsonSchemaForUser(isCloudVersion));
                                put("updated", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("visibility", jsonSchemaForVisibility());
                                put("started", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("timeSpent", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("timeSpentSeconds", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                            }}).build());
                    put("updated", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                    put("timeTracking", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{
                                put("originalEstimate", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("remainingEstimate", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("timeSpent", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("originalEstimateSeconds", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                                put("remainingEstimateSeconds", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                                put("timeSpentSeconds", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                            }}).build());

                    put("created", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("resolutiondate", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("lastViewed", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("assignee", jsonSchemaForUser(isCloudVersion));
                    put("status", jsonSchemaForIssueStatus());
                    put("creator", jsonSchemaForUser(isCloudVersion));
                    put("reporter", jsonSchemaForUser(isCloudVersion));

                    put("aggregateprogress", jsonSchemaForProgressInformation());

                    put("duedate", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());

                    put("progress", jsonSchemaForProgressInformation());

                    put("votes", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{
                                put("votes", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                                put("hasVoted", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("boolean").build());
                            }}).build());

                }})
                .build();
    }

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForIssuePriority() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("name", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                }})
                .build();
    }

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForIssueType() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("subtask", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("boolean").build());
                    put("hierarchyLevel", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                }})
                .build();
    }

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForIssueParent() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility

                    put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("fields",
                            JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                                    .type("object")
                                    .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                        put("status", jsonSchemaForIssueStatus());
                                        put("priority", jsonSchemaForIssuePriority());
                                        put("issuetype", jsonSchemaForIssueType());
                                    }})
                                    .build()
                    );

                }})
                .build();
    }

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForIssueStatus() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("name", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("statusCategory", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("key", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("colorName", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("name", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                            }})
                            .build());
                }})
                .build();
    }

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForUser(boolean isCloudVersion) {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put(isCloudVersion ? "accountId" : "key", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("accountType", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("emailAddress", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("active", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("boolean").build());
                }})
                .build();
    }

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForLinks() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("array")
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("type", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("inward", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("outward", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                            }}).build());
                    put("outwardIssue", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("object").items(JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                            }}).build()).build());
                    put("inwardIssue", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("object").items(JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                            }}).build()).build());
                }}).build();
    }

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForVisibility() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{
                    put("type", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("value", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("identifier", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                }}).build();
    }

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForProgressInformation() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{
                    put("progress", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                    put("total", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                }}).build();
    }
}