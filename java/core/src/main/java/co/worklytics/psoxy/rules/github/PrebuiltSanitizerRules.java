package co.worklytics.psoxy.rules.github;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.ParameterSchema;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
public class PrebuiltSanitizerRules {

    private static final  String COPILOT_AUDIT_LOG_MANDATORY_QUERY_PARAMETERS_REGEX = "\\\\?(?=.*[\\\\?&]phrase=)(?=.*[\\\\?&]include=).*$";

    private static final List<String> commonAllowedQueryParameters = Lists.newArrayList(
        "per_page",
        "page"
    );

    private static final List<String> userAllowedQueryParameters = Lists.newArrayList(
        "since",
        "per_page"
    );

    private static final List<String> commonDirectionAllowedQueryParameters = Lists.newArrayList(
        "sort",
        "direction"
    );

    private final static Map<String, ParameterSchema> COPILOT_AUDIT_LOG_QUERY_SUPPORTED_PARAMETER_SCHEMA = ImmutableMap.of(
        "phrase", ParameterSchema.builder()
            .pattern("(action:copilot(?:\\+created:\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z\\.\\.\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z)?)")
            .build(),
        "include", ParameterSchema.builder()
            .enumValues(List.of("web"))
            .build()
    );

    private static final List<String> orgMembersAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            Lists.newArrayList("filter",
                "role").stream())
        .collect(Collectors.toList());

    private static final List<String> orgAuditLogAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            Lists.newArrayList("phrase",
                "include",
                "after",
                "before",
                "order").stream())
        .collect(Collectors.toList());

    private static final List<String> issuesAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            commonDirectionAllowedQueryParameters.stream(),
            Lists.newArrayList("milestone",
                "state",
                "labels",
                "since").stream())
        .collect(Collectors.toList());

    private static final List<String> issueCommentsQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            Lists.newArrayList("since").stream())
        .collect(Collectors.toList());

    private static final List<String> repoListAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            commonDirectionAllowedQueryParameters.stream(),
            Lists.newArrayList("type").stream())
        .collect(Collectors.toList());

    private static final List<String> repoBranchesAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            Lists.newArrayList("protected").stream())
        .collect(Collectors.toList());

    private static final List<String> pullsAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            commonDirectionAllowedQueryParameters.stream(),
            Lists.newArrayList("state",
                "head",
                "base").stream())
        .collect(Collectors.toList());

    private static final List<String> pullsCommentsQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            commonDirectionAllowedQueryParameters.stream(),
            Lists.newArrayList("since").stream())
        .collect(Collectors.toList());

    private static final List<String> commitsAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
            Lists.newArrayList("sha",
                "path",
                "since",
                "until").stream())
        .collect(Collectors.toList());

    private final static JsonSchemaFilter GIT_ACTOR_JSON_SCHEMA = JsonSchemaFilter.builder()
        .type("object")
        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
            put("email", JsonSchemaFilter.builder().type("string").build());
            put("date", JsonSchemaFilter.builder().type("string").build());
            put("user", JsonSchemaFilter.builder().type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {
                    {
                        put("login", JsonSchemaFilter.builder().type("string").build());
                    }
                }).build());
        }}).build();

    private final static JsonSchemaFilter COMMIT_JSON_SCHEMA = JsonSchemaFilter.builder()
        .type("object")
        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
            put("id", JsonSchemaFilter.builder().type("string").build());
            put("oid", JsonSchemaFilter.builder().type("string").build());
            put("user", JsonSchemaFilter.builder().type("onBehalfOf")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {
                    {
                        put("login", JsonSchemaFilter.builder().type("string").build());
                        put("email", JsonSchemaFilter.builder().type("string").build());
                        put("id", JsonSchemaFilter.builder().type("string").build());
                    }
                }).build());
            put("authoredDate", JsonSchemaFilter.builder().type("string").build());
            put("authoredByCommitter", JsonSchemaFilter.builder().type("boolean").build());
            put("changedFilesIfAvailable", JsonSchemaFilter.builder().type("integer").build());
            put("committedDate", JsonSchemaFilter.builder().type("string").build());
            put("committedViaWeb", JsonSchemaFilter.builder().type("boolean").build());
            put("parents", JsonSchemaFilter.builder().type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {
                    {
                        put("nodes", JsonSchemaFilter.builder().type("array")
                            .items(JsonSchemaFilter.builder().type("object")
                                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {
                                    {
                                        put("oid", JsonSchemaFilter.builder().type("string").build());
                                    }
                                }).build()).build());
                    }
                }).build());
            put("author", GIT_ACTOR_JSON_SCHEMA);
            put("committer", GIT_ACTOR_JSON_SCHEMA);
            put("associatedPullRequests", JsonSchemaFilter.builder().type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {
                    {
                        put("nodes", JsonSchemaFilter.builder().type("array")
                            .items(JsonSchemaFilter.builder().type("object")
                                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {
                                    {
                                        put("number", JsonSchemaFilter.builder().type("integer").build());
                                    }
                                }).build()).build());
                    }
                }).build());
            put("tree", JsonSchemaFilter.builder().type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {
                    {
                        put("oid", JsonSchemaFilter.builder().type("string").build());
                    }
                }).build());
            put("additions", JsonSchemaFilter.builder().type("integer").build());
            put("deletions", JsonSchemaFilter.builder().type("integer").build());
        }}).build();

    private final static JsonSchemaFilter PULL_COMMIT_SCHEMA = JsonSchemaFilter.builder().type("object")
        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
            put("commits", JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                    put("pageInfo", jsonSchemaForPageInfo());
                    put("edges", JsonSchemaFilter.builder()
                        .type("array")
                        .items(JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                put("node", JsonSchemaFilter.builder()
                                    .type("object")
                                    .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                        put("commit", COMMIT_JSON_SCHEMA);
                                    }}).build());
                            }})
                            .build())
                        .build());
                }}).build());
        }}).build();

    private final static JsonSchemaFilter REPOSITORY_COMMIT_SCHEMA = JsonSchemaFilter.builder()
        .type("object")
        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
            put("target", JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                    put("history", JsonSchemaFilter.builder()
                        .type("object")
                        .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                            put("pageInfo", jsonSchemaForPageInfo());
                            put("edges", jsonSchemaForEdge(COMMIT_JSON_SCHEMA));
                        }}).build());
                }}).build());
        }}).build();


    private static final Transform TEAM_REDACTS = Transform.Redact.builder()
        .jsonPath("$..name")
        .jsonPath("$..description")
        .jsonPath("$..url")
        .jsonPath("$..html_url")
        .jsonPath("$..members_url")
        .jsonPath("$..repositories_url")
        .build();

    private static final Transform TEAM_SLUG_TOKENIZATION = Transform.Tokenize.builder()
        .jsonPath("$..slug")
        .build();

    static final Endpoint ORG_MEMBERS = Endpoint.builder()
        .pathTemplate("/orgs/{org}/members")
        .allowedQueryParams(orgMembersAllowedQueryParameters)
        .transforms(generateUserTransformations("."))
        .build();

    // https://docs.github.com/en/rest/users/users?apiVersion=2022-11-28#list-users
    static final Endpoint USERS = Endpoint.builder()
        .pathTemplate("/users/{username}")
        .pathParameterSchemas(ImmutableMap.of("username", ParameterSchema.reversiblePseudonym()))
        .allowedQueryParams(userAllowedQueryParameters)
        .transforms(generateUserTransformations("."))
        .build();

    static final Endpoint GRAPHQL_FOR_USERS = Endpoint.builder()
        .pathTemplate("/graphql")
        .transform(Transform.Redact.builder()
            .jsonPath("$..ssoUrl")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..nameId")
            .jsonPath("$..email")
            .jsonPath("$..emails[*].value")
            .jsonPath("$..guid")
            .jsonPath("$..organizationVerifiedDomainEmails[*]")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .includeReversible(true)
            .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .jsonPath("$..login")
            .build())
        .responseSchema(jsonSchemaForGraphQLQueryResult())
        .build();

    static final Endpoint ORG_TEAMS = Endpoint.builder()
        .pathTemplate("/orgs/{org}/teams")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(TEAM_REDACTS)
        .transform(TEAM_SLUG_TOKENIZATION)
        .build();

    static final Endpoint ORG_AUDIT_LOG = Endpoint.builder()
        .pathTemplate("/orgs/{org}/audit-log")
        .allowedQueryParams(orgAuditLogAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..hashed_token")
            .jsonPath("$..business")
            .jsonPath("$..business_id")
            .jsonPath("$..transport_protocol")
            .jsonPath("$..transport_protocol_name")
            .jsonPath("$..pull_request_title")
            .jsonPath("$..user_agent")
            .jsonPath("$..job")
            .jsonPath("$..active_job_id")
            .jsonPath("$..aqueduct_job_id")
            .jsonPath("$..catalog_service")
            .jsonPath("$..migration_id")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..actor")
            .jsonPath("$..user")
            .jsonPath("$..user_id")
            .jsonPath("$..started_by")
            .build())
        .build();

    static final Endpoint ORG_AUDIT_LOG_WITH_INSTALLATION_ID = Endpoint.builder()
        .pathTemplate("/organizations/{installationId}/audit-log")
        .allowedQueryParams(orgAuditLogAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..hashed_token")
            .jsonPath("$..business")
            .jsonPath("$..business_id")
            .jsonPath("$..transport_protocol")
            .jsonPath("$..transport_protocol_name")
            .jsonPath("$..pull_request_title")
            .jsonPath("$..user_agent")
            .jsonPath("$..job")
            .jsonPath("$..active_job_id")
            .jsonPath("$..aqueduct_job_id")
            .jsonPath("$..catalog_service")
            .jsonPath("$..migration_id")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..actor")
            .jsonPath("$..user")
            .jsonPath("$..user_id")
            .jsonPath("$..started_by")
            .build())
        .build();

    static final Endpoint ORG_TEAM_MEMBERS = Endpoint.builder()
        .pathTemplate("/orgs/{org}/teams/{teamSlug}/members")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transforms(generateUserTransformations("."))
        .build();

    static final Endpoint ORG_COPILOT_SEATS = Endpoint.builder()
        .pathTemplate("/orgs/{org}/copilot/billing/seats")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transforms(generateUserTransformations("..", Collections.singletonList("assignee")))
        .transform(TEAM_REDACTS)
        .transform(TEAM_SLUG_TOKENIZATION)
        .build();

    static final Endpoint REPO_COMMIT = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/commits/{ref}")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..name")
            .jsonPath("$..message")
            .jsonPath("$..files")
            .jsonPath("$..signature")
            .jsonPath("$..payload")
            .build())
        .transforms(generateUserTransformations("..", Arrays.asList("author", "committer")))
        .build();

    static final Endpoint ISSUES = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/issues")
        .allowedQueryParams(issuesAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..title")
            .jsonPath("$..body")
            .jsonPath("$..description")
            .jsonPath("$..name")
            .build())
        .transforms(generateUserTransformations("..", Arrays.asList(
            // Owner can be a user or an organization user
            "owner",
            "user",
            "assignee",
            "creator",
            "closed_by")))
        // Seems array plus other object in filtering is not matching the json path, so a different rule for this
        .transforms(generateUserTransformations("..assignees[*]", Collections.emptyList()))
        .build();

    static final Endpoint ISSUE = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/issues/{issueNumber}")
        .transform(Transform.Redact.builder()
            .jsonPath("$..title")
            .jsonPath("$..body")
            .jsonPath("$..description")
            .jsonPath("$..name")
            .jsonPath("$..pem")
            .build())
        .transforms(generateUserTransformations("..", Arrays.asList("user", "assignee", "creator", "closed_by")))
        .transforms(generateUserTransformations("..assignees[*]", Collections.emptyList()))
        .build();

    static final Endpoint ISSUE_COMMENTS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/issues/{issueNumber}/comments")
        .allowedQueryParams(issueCommentsQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..url")
            .jsonPath("$..message")
            .jsonPath("$..body")
            .build())
        .transforms(generateUserTransformations("..", Collections.singletonList("user")))
        .build();

    static final Endpoint ISSUE_EVENTS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/issues/{issueNumber}/events")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..name")
            .jsonPath("$..url")
            .jsonPath("$..body")
            .jsonPath("$..rename")
            .jsonPath("$..name")
            .jsonPath("$..message")
            .jsonPath("$..files")
            .jsonPath("$..signature")
            .jsonPath("$..payload")
            .jsonPath("$..dismissalMessage")
            .build())
        .transforms(generateUserTransformations("..", Arrays.asList(
            // Owner can be a user or an organization user
            "owner",
            "user",
            "actor",
            "assignee",
            "assigner",
            "creator",
            "closed_by",
            "author",
            "committer",
            "requested_reviewer",
            "review_requester")))
        .build();

    static final Endpoint ISSUE_TIMELINE = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/issues/{issueNumber}/timeline")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..name")
            .jsonPath("$..url")
            .jsonPath("$..body")
            .jsonPath("$..rename")
            .jsonPath("$..name")
            .jsonPath("$..message")
            .jsonPath("$..files")
            .jsonPath("$..signature")
            .jsonPath("$..payload")
            .jsonPath("$..dismissalMessage")
            .build())
        .transforms(generateUserTransformations("..", Arrays.asList(
            // Owner can be a user or an organization user
            "owner",
            "user",
            "actor",
            "assignee",
            "assigner",
            "creator",
            "closed_by",
            "author",
            "committer",
            "requested_reviewer",
            "review_requester")))
        .build();

    static final Endpoint PULL_REVIEWS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/pulls/{pullNumber}/reviews")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..body")
            .jsonPath("$..html_url")
            .jsonPath("$..pull_request_url")
            .build())
        .transforms(generateUserTransformations("..", Collections.singletonList("user")))
        .build();

    static final Endpoint PULLS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/pulls")
        .allowedQueryParams(pullsAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..title")
            .jsonPath("$..body")
            .jsonPath("$..name")
            .jsonPath("$..description")
            .jsonPath("$..url")
            .jsonPath("$..homepage")
            .jsonPath("$..commit_title")
            .jsonPath("$..commit_message")
            .build())
        .transforms(generateUserTransformations("..", Arrays.asList(
            // Owner can be a user or an organization user
            "owner",
            "user",
            "actor",
            "assignee",
            "creator",
            "merged_by",
            "closed_by",
            "enabled_by")))
        .transforms(generateUserTransformations("..requested_reviewers[*]", Collections.emptyList()))
        .transforms(generateUserTransformations("..assignees[*]", Collections.emptyList()))
        .build();

    static final Endpoint PULL = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/pulls/{pullNumber}")
        .transform(Transform.Redact.builder()
            .jsonPath("$..title")
            .jsonPath("$..body")
            .jsonPath("$..name")
            .jsonPath("$..description")
            .jsonPath("$..homepage")
            .jsonPath("$..commit_title")
            .jsonPath("$..commit_message")
            .build())
        .transforms(generateUserTransformations("..", Arrays.asList(
            // Owner can be a user or an organization user
            "owner",
            "user",
            "actor",
            "assignee",
            "creator",
            "merged_by",
            "closed_by",
            "enabled_by")))
        .transforms(generateUserTransformations("..requested_reviewers[*]", Collections.emptyList()))
        .transforms(generateUserTransformations("..assignees[*]", Collections.emptyList()))
        .build();

    static final Endpoint REPOSITORIES = Endpoint.builder()
        .pathTemplate("/orgs/{org}/repos")
        .allowedQueryParams(repoListAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..description")
            .jsonPath("$..homepage")
            .build())
        // Owner can be a user or an organization user
        .transforms(generateUserTransformations("..", Collections.singletonList("owner")))
        .build();

    // https://docs.github.com/en/rest/commits/comments?apiVersion=2022-11-28#list-commit-comments
    static final Endpoint COMMIT_COMMENTS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/commits/{commitSha}/comments")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..name")
            .jsonPath("$..message")
            .jsonPath("$..files")
            .jsonPath("$..signature")
            .jsonPath("$..payload")
            .jsonPath("$..path")
            .jsonPath("$..body")
            .jsonPath("$..position")
            .jsonPath("$..line")
            .build())
        .transforms(generateUserTransformations("..", Collections.singletonList("user")))
        .build();

    static final Endpoint COMMIT_COMMENT_REACTIONS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/comments/{commentId}/reactions")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transforms(generateUserTransformations("..", Collections.singletonList("user")))
        .build();

    static final Endpoint ISSUE_REACTIONS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/issues/{issueNumber}/reactions")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transforms(generateUserTransformations("..", Collections.singletonList("user")))
        .build();

    static final Endpoint ISSUE_COMMENT_REACTIONS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/issues/comments/{commentId}/reactions")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transforms(generateUserTransformations("..", Collections.singletonList("user")))
        .build();

    static final Endpoint REPO_EVENTS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/events")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..display_login")
            .jsonPath("$..avatar_url")
            .jsonPath("$..gravatar_id")
            .jsonPath("$..html_url")
            .jsonPath("$..name")
            .jsonPath("$..url")
            .jsonPath("$..message")
            .jsonPath("$..description")
            .jsonPath("$..body")
            .jsonPath("$..title")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..author.email")
            .build())
        .transforms(generateUserTransformations("..", Arrays.asList(
            // Owner can be a user or an organization user
            "owner",
            "user",
            "actor",
            "assignee",
            "requested_reviewers[*]", "creator", "merged_by", "closed_by")))
        .transforms(generateUserTransformations("..assignees[*]", Collections.emptyList()))
        .build();

    static final Endpoint REPO_COMMITS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/commits")
        .allowedQueryParams(commitsAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..name")
            .jsonPath("$..message")
            .jsonPath("$..files")
            .jsonPath("$..signature")
            .jsonPath("$..payload")
            .build())
        .transforms(generateUserTransformations("..", Arrays.asList("author", "committer")))
        .build();

    static final Endpoint REPO_BRANCHES = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/branches")
        .allowedQueryParams(repoBranchesAllowedQueryParameters)
        .build();

    static final Endpoint PULL_COMMITS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/pulls/{pullNumber}/commits")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..name")
            .jsonPath("$..message")
            .jsonPath("$..files")
            .jsonPath("$..signature")
            .jsonPath("$..payload")
            .build())
        .transforms(generateUserTransformations("..", Arrays.asList("author", "committer")))
        .build();

    static final Endpoint PULL_COMMENTS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/pulls/{pullNumber}/comments")
        .allowedQueryParams(pullsCommentsQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..path")
            .jsonPath("$..diff_hunk")
            .jsonPath("$..body")
            .build())
        .transforms(generateUserTransformations("..", Collections.singletonList("user")))
        .build();

    static final Endpoint PULL_REVIEW_COMMENTS = Endpoint.builder()
        .pathTemplate("/repos/{owner}/{repo}/pulls/{pullNumber}/reviews/{reviewId}/comments")
        .allowedQueryParams(commonAllowedQueryParameters)
        .transform(Transform.Redact.builder()
            .jsonPath("$..path")
            .jsonPath("$..diff_hunk")
            .jsonPath("$..body")
            .build())
        .transforms(generateUserTransformations("..", Collections.singletonList("user")))
        .build();

    @VisibleForTesting
    static final RESTRules GITHUB = Rules2.builder()
        .endpoint(ORG_MEMBERS)
        .endpoint(USERS)
        .endpoint(GRAPHQL_FOR_USERS)
        .endpoint(ORG_TEAMS)
        .endpoint(ORG_TEAM_MEMBERS)
        .endpoint(ORG_AUDIT_LOG)
        .endpoint(ORG_AUDIT_LOG_WITH_INSTALLATION_ID)
        .endpoint(REPOSITORIES)
        .endpoint(REPO_BRANCHES)
        .endpoint(REPO_COMMITS)
        .endpoint(REPO_COMMIT)
        .endpoint(REPO_EVENTS)
        .endpoint(COMMIT_COMMENTS)
        .endpoint(COMMIT_COMMENT_REACTIONS)
        .endpoint(ISSUE)
        .endpoint(ISSUES)
        .endpoint(ISSUE_COMMENTS)
        .endpoint(ISSUE_EVENTS)
        .endpoint(ISSUE_TIMELINE)
        .endpoint(ISSUE_REACTIONS)
        .endpoint(ISSUE_COMMENT_REACTIONS)
        .endpoint(PULL_REVIEWS)
        .endpoint(PULLS)
        .endpoint(PULL_COMMENTS)
        .endpoint(PULL_REVIEW_COMMENTS)
        .endpoint(PULL_COMMITS)
        .endpoint(PULL)
        .build();

    @VisibleForTesting
    static final RESTRules GITHUB_COPILOT = Rules2.builder()
        .endpoint(ORG_MEMBERS)
        .endpoint(USERS)
        .endpoint(GRAPHQL_FOR_USERS)
        .endpoint(ORG_TEAMS)
        .endpoint(ORG_TEAM_MEMBERS)
        .endpoint(ORG_COPILOT_SEATS)
        .endpoint(ORG_AUDIT_LOG
            // Ensure that phrase and include query parameters are present
            .withPathRegex("^/orgs/[^/]+/audit-log" + COPILOT_AUDIT_LOG_MANDATORY_QUERY_PARAMETERS_REGEX)
            .withQueryParamSchemas(COPILOT_AUDIT_LOG_QUERY_SUPPORTED_PARAMETER_SCHEMA))
        .endpoint(ORG_AUDIT_LOG_WITH_INSTALLATION_ID
            // Ensure that phrase and include query parameters are present
            .withPathRegex("^/organizations\\/\\d+\\/audit-log" + COPILOT_AUDIT_LOG_MANDATORY_QUERY_PARAMETERS_REGEX)
            .withQueryParamSchemas(COPILOT_AUDIT_LOG_QUERY_SUPPORTED_PARAMETER_SCHEMA))
        .build();

    @VisibleForTesting
    static final RESTRules GITHUB_ENTERPRISE_SERVER = Rules2.builder()
        // NOTE: Enterprise Server endpoints are with /api/v3/ prefix for all requests,
        // except for GraphQL which is just /api/
        // Leaving {enterpriseServerVersion} in the path template open to support future versions without changing rules
        .endpoint(ORG_MEMBERS.withPathTemplate("/api/{enterpriseServerVersion}/orgs/{org}/members"))
        .endpoint(USERS.withPathTemplate("/api/{enterpriseServerVersion}/users/{username}"))
        .endpoint(GRAPHQL_FOR_USERS.withPathTemplate("/api/graphql"))
        .endpoint(ORG_TEAMS.withPathTemplate("/api/{enterpriseServerVersion}/orgs/{org}/teams"))
        .endpoint(ORG_TEAM_MEMBERS.withPathTemplate("/api/{enterpriseServerVersion}/orgs/{org}/teams/{teamSlug}/members"))
        .endpoint(ORG_AUDIT_LOG.withPathTemplate("/api/{enterpriseServerVersion}/orgs/{org}/audit-log"))
        .endpoint(ORG_AUDIT_LOG_WITH_INSTALLATION_ID.withPathTemplate("/api/{enterpriseServerVersion}/organizations/{installationId}/audit-log"))
        .endpoint(REPOSITORIES.withPathTemplate("/api/{enterpriseServerVersion}/orgs/{org}/repos"))
        .endpoint(REPO_BRANCHES.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/branches"))
        .endpoint(REPO_COMMITS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/commits"))
        .endpoint(REPO_COMMIT.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/commits/{ref}"))
        .endpoint(REPO_EVENTS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/events"))
        .endpoint(COMMIT_COMMENTS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/commits/{commitSha}/comments"))
        .endpoint(COMMIT_COMMENT_REACTIONS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/comments/{commentId}/reactions"))
        .endpoint(ISSUE.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/issues/{issueNumber}"))
        .endpoint(ISSUES.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/issues"))
        .endpoint(ISSUE_COMMENTS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/issues/{issueNumber}/comments"))
        .endpoint(ISSUE_EVENTS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/issues/{issueNumber}/events"))
        .endpoint(ISSUE_TIMELINE.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/issues/{issueNumber}/timeline"))
        .endpoint(ISSUE_REACTIONS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/issues/{issueNumber}/reactions"))
        .endpoint(ISSUE_COMMENT_REACTIONS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/issues/comments/{commentId}/reactions"))
        .endpoint(PULL_REVIEWS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/pulls/{pullNumber}/reviews"))
        .endpoint(PULLS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/pulls"))
        .endpoint(PULL_COMMENTS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/pulls/{pullNumber}/comments"))
        .endpoint(PULL_REVIEW_COMMENTS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/pulls/{pullNumber}/reviews/{reviewId}/comments"))
        .endpoint(PULL_COMMITS.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/pulls/{pullNumber}/commits"))
        .endpoint(PULL.withPathTemplate("/api/{enterpriseServerVersion}/repos/{owner}/{repo}/pulls/{pullNumber}"))
        .build();

    public static final Map<String, RESTRules> RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("github", GITHUB)
            .put("github-copilot", GITHUB_COPILOT)
            .put("github-enterprise-server", GITHUB_ENTERPRISE_SERVER)
            .build();

    private static List<Transform> generateUserTransformations(String prefix) {
        return generateUserTransformations(prefix, Collections.emptyList());
    }

    private static List<Transform> generateUserTransformations(String prefix, List<String> userObjectNames) {
        String objectNames = "";

        if (!userObjectNames.isEmpty()) {
            objectNames = String.format("[%s]", StringUtils.join(userObjectNames.stream()
                .map(i -> String.format("'%s'", i))
                .collect(Collectors.toSet()), ","));
        }

        return Arrays.asList(
            // Following expression works for subproperties:
            // $..['user', 'owner', ... ]['avatar_url','gravatar_id',...]
            // but one there is no property and it is on the root, it is not working:
            // $..['avatar_url','gravatar_id',...]
            // so to have that working, in root case they are expanded instead of put them as an array
            prefix.equals(".") ? Transform.Redact.builder()
                .jsonPath(String.format("$%s%s.avatar_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.gravatar_id", prefix, objectNames))
                .jsonPath(String.format("$%s%s.url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.html_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.followers_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.following_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.gists_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.starred_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.subscriptions_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.organizations_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.repos_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.events_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.received_events_url", prefix, objectNames))
                .jsonPath(String.format("$%s%s.name", prefix, objectNames))
                .jsonPath(String.format("$%s%s.company", prefix, objectNames))
                .jsonPath(String.format("$%s%s.location", prefix, objectNames))
                .jsonPath(String.format("$%s%s.bio", prefix, objectNames))
                .jsonPath(String.format("$%s%s.twitter_username", prefix, objectNames))
                .build() :
                Transform.Redact.builder()
                    .jsonPath(String.format("$%s%s.['avatar_url'," +
                        "'gravatar_id'," +
                        "'url'," +
                        "'html_url'," +
                        "'followers_url'," +
                        "'following_url'," +
                        "'gists_url'," +
                        "'starred_url'," +
                        "'subscriptions_url'," +
                        "'organizations_url'," +
                        "'repos_url'," +
                        "'events_url'," +
                        "'received_events_url'," +
                        "'name'," +
                        "'company'," +
                        "'location'," +
                        "'bio'," +
                        "'twitter_username']", prefix, objectNames))
                    .build(),

            prefix.equals(".") ?
                Transform.Pseudonymize.builder()
                    .jsonPath(String.format("$%s%s.id", prefix, objectNames))
                    .jsonPath(String.format("$%s%s.node_id", prefix, objectNames))
                    .jsonPath(String.format("$%s%s.email", prefix, objectNames))
                    .build() :
                Transform.Pseudonymize.builder()
                    .jsonPath(String.format("$%s%s.['id','node_id','email']", prefix, objectNames))
                    .build()
            ,
            Transform.Pseudonymize.builder()
                .includeReversible(true)
                .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                .jsonPath(String.format("$%s%s.login", prefix, objectNames))
                .build()
        );
    }

    private static JsonSchemaFilter jsonSchemaForGraphQLQueryResult() {
        return JsonSchemaFilter.builder()
            .type("object")
            // Using LinkedHashMap to keep the order to support same
            // YAML serialization result
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                put("data", JsonSchemaFilter.<String, RESTRules>builder()
                    .type("object")
                    .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                        put("organization", JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                                put("samlIdentityProvider", jsonSchemaForOrganizationProperty("externalIdentities", jsonSchemaForSamlNode()));
                                put("membersWithRole", jsonSchemaForQueryResult(jsonSchemaForMemberNode()));
                                put("repository", JsonSchemaFilter.builder()
                                    .type("object")
                                    .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                                        put("ref", REPOSITORY_COMMIT_SCHEMA);
                                        put("pullRequest", PULL_COMMIT_SCHEMA);
                                    }})
                                    .build());
                            }}).build());
                    }}).build());
                put("errors", jsonSchemaForErrors());
            }}).build();
    }

    private static JsonSchemaFilter jsonSchemaForOrganizationProperty(String propertyName, JsonSchemaFilter node) {
        return JsonSchemaFilter.builder()
            .type("object")
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                put("externalIdentities", jsonSchemaForQueryResult(node));
            }}).build();
    }

    private static JsonSchemaFilter jsonSchemaForQueryResult(JsonSchemaFilter node) {
        return JsonSchemaFilter.builder()
            .type("object")
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                put("pageInfo", jsonSchemaForPageInfo());
                put("edges", jsonSchemaForEdge(node));
            }}).build();
    }

    private static JsonSchemaFilter jsonSchemaForPageInfo() {
        return JsonSchemaFilter.builder()
            .type("object")
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                put("hasNextPage", JsonSchemaFilter.builder().type("boolean").build());
                put("endCursor", JsonSchemaFilter.builder().type("string").build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForEdge(JsonSchemaFilter node) {
        return JsonSchemaFilter.builder()
            .type("array")
            .items(JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("node", node);
                }})
                .build())
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForSamlNode() {
        return JsonSchemaFilter.builder()
            .type("object")
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                put("guid", JsonSchemaFilter.builder().type("string").build());
                put("samlIdentity", JsonSchemaFilter.builder().type("object").properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                    put("nameId", JsonSchemaFilter.builder().type("string").build());
                    put("emails", JsonSchemaFilter.builder()
                        .type("array")
                        .items(JsonSchemaFilter.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                put("value", JsonSchemaFilter.builder().type("string").build());
                            }})
                            .build())
                        .build());
                }}).build());
                put("user", JsonSchemaFilter.builder().type("object").properties(new LinkedHashMap<String, JsonSchemaFilter>() {{
                    put("login", JsonSchemaFilter.builder().type("string").build());
                }}).build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForMemberNode() {
        return JsonSchemaFilter.builder()
            .type("object")
            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                put("email", JsonSchemaFilter.builder().type("string").build());
                put("login", JsonSchemaFilter.builder().type("string").build());
                put("id", JsonSchemaFilter.builder().type("string").build());
                put("isSiteAdmin", JsonSchemaFilter.builder().type("boolean").build());
                put("organizationVerifiedDomainEmails", JsonSchemaFilter.builder()
                    .type("array")
                    .items(JsonSchemaFilter.builder().type("string").build())
                    .build());
            }})
            .build();
    }

    private static JsonSchemaFilter jsonSchemaForErrors() {
        return JsonSchemaFilter.<String, RESTRules>builder()
            .type("array")
            .items(JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("type", JsonSchemaFilter.builder().type("string").build());
                    put("path", JsonSchemaFilter.builder().type("array")
                        .items(JsonSchemaFilter.builder().type("string").build()).build());
                    put("locations", JsonSchemaFilter.builder().type("array")
                        .items(JsonSchemaFilter.builder().type("object")
                            .properties(new LinkedHashMap<String, JsonSchemaFilter>() {
                                {
                                    put("line", JsonSchemaFilter.builder().type("integer").build());
                                    put("column", JsonSchemaFilter.builder().type("integer").build());
                                }
                            }).build()).build());
                    put("message", JsonSchemaFilter.builder().type("string").build());
                }}).build()).build();
    }
}
