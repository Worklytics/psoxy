package co.worklytics.psoxy.rules.jira;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilterUtils;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;

import java.util.LinkedHashMap;
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

    static final Endpoint ISSUE_SEARCH = Endpoint.builder()
            .pathTemplate("/ex/jira/{cloudId}/rest/api/3/search")
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
                    .jsonPath("$.issues[*]..body..id")
                    .build())
            .responseSchema(jsonSchemaForQueryResult())
            .build();

    static final Endpoint ISSUE_CHANGELOG = Endpoint.builder()
            .pathTemplate("/ex/jira/{cloudId}/rest/api/3/issue/{issueId}/changelog")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.values[*]..self")
                    .jsonPath("$.values[*]..avatarUrls")
                    .jsonPath("$.values[*]..displayName")
                    .jsonPath("$.values[*].items[*].from")
                    .jsonPath("$.values[*].items[*].to")
                    .jsonPath("$.values[*].items[*].fromString")
                    .jsonPath("$.values[*].items[*].toString")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.values[*]..accountId")
                    .jsonPath("$.values[*]..emailAddress")
                    .build())
            .build();

    static final Endpoint ISSUE_COMMENT = Endpoint.builder()
            .pathTemplate("/ex/jira/{cloudId}/rest/api/3/issue/{issueId}/comment")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.comments[*]..self")
                    .jsonPath("$.comments[*]..avatarUrls")
                    .jsonPath("$.comments[*]..displayName")
                    .jsonPath("$.comments[*]..text")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.comments[*]..accountId")
                    .jsonPath("$.comments[*].body..id")
                    .jsonPath("$.comments[*]..emailAddress")
                    .build())
            .build();

    static final Endpoint ISSUE_WORKLOG = Endpoint.builder()
            .pathTemplate("/ex/jira/{cloudId}/rest/api/3/issue/{issueId}/worklog")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.worklogs[*]..self")
                    .jsonPath("$.worklogs[*]..avatarUrls")
                    .jsonPath("$.worklogs[*]..displayName")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.worklogs[*]..accountId")
                    .jsonPath("$.worklogs[*]..emailAddress")
                    .build())
            .build();

    static final Endpoint USERS = Endpoint.builder()
            .pathTemplate("/ex/jira/{cloudId}/rest/api/3/users")
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
            .pathTemplate("/ex/jira/{cloudId}/rest/api/3/group/bulk")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$..name")
                    .build())
            .build();

    static final Endpoint GROUP_MEMBER = Endpoint.builder()
            .pathTemplate("/ex/jira/{cloudId}/rest/api/3/group/member")
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

    public static final RESTRules JIRA = Rules2.builder()
            .endpoint(GROUP_BULK)
            .endpoint(GROUP_MEMBER)
            .endpoint(ISSUE_SEARCH)
            .endpoint(ISSUE_CHANGELOG)
            .endpoint(ISSUE_COMMENT)
            .endpoint(ISSUE_WORKLOG)
            .endpoint(USERS)
            .build();

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForQueryResult() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility

                    put("startAt", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
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
                            .items(jsonSchemaForIssue())
                            .build());
                }})
                .build();
    }


    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForIssue() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                    put("fields", jsonSchemaForIssueFields());
                }})
                .build();
    }

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForIssueFields() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
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
                                put("watchers", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("array").items(jsonSchemaForUser()).build());
                            }}).build());
                    put("attachment", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("array")
                            .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                put("id", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                                put("author", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("object").items(jsonSchemaForUser()).build());
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
                                put("author", jsonSchemaForUser());
                                put("updateAuthor", jsonSchemaForUser());
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
                                put("author", jsonSchemaForUser());
                                put("updateAuthor", jsonSchemaForUser());
                                put("updated", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("visibility", jsonSchemaForVisibility());
                                put("started", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("timeSpent", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
                                put("timeSpentSeconds", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                            }}).build());
                    put("updated", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("integer").build());
                    put("timetracking", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
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
                    put("assignee", jsonSchemaForUser());
                    put("status", jsonSchemaForIssueStatus());
                    put("creator", jsonSchemaForUser());
                    put("reporter", jsonSchemaForUser());

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
                                        put("status",jsonSchemaForIssueStatus());
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

    private static JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaForUser() {
        return JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilterUtils.JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("accountId", JsonSchemaFilterUtils.JsonSchemaFilter.builder().type("string").build());
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