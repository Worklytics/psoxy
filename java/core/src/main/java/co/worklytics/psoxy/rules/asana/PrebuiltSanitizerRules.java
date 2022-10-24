package co.worklytics.psoxy.rules.asana;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.Transform;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PrebuiltSanitizerRules {

    private static final List<String> commonAllowedQueryParameters = Lists.newArrayList(
            "limit",
            "offset",
            "opt_pretty",
            "opt_fields"
    );

    private static final List<String> taskAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
                    Lists.newArrayList(
                            "assignee",
                            "project",
                            "section",
                            "workspace",
                            "completed_since",
                            "modified_since").stream())
            .collect(Collectors.toList());

    private static final List<String> teamsAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
                    Lists.newArrayList("archived").stream())
            .collect(Collectors.toList());

    private static final List<String> usersAllowedQueryParameters = Streams.concat(commonAllowedQueryParameters.stream(),
                    Lists.newArrayList("workspace", "team").stream())
            .collect(Collectors.toList());

    private static final List<String> searchTaskByWorkspaceAllowedQueryParameters = Lists.newArrayList(
            "limit",
            "modified_at.after",
            "modified_at.before",
            "is_subtask",
            "sort_ascending"
    );

    static final Rules2.Endpoint WORKSPACES = Rules2.Endpoint.builder()
            .pathRegex("^/api/1.0/workspaces[?]?[^/]*$")
            .allowedQueryParams(commonAllowedQueryParameters)
            //no redaction/pseudonymization
            // current UX for Asana connector lets users specify workspace by name, so can't redact it;
            // and we don't expect Workspace names to be sensitive or PII.
            .build();

    static final Rules2.Endpoint USERS = Rules2.Endpoint.builder()
            .pathRegex("^/api/1.0/users[?]?[^/]*")
            .allowedQueryParams(usersAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.data[*].name")
                    .jsonPath("$.data[*].photo")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.data[*].email")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .includeReversible(true)
                    .jsonPath("$.data[*].gid")
                    .build())
            .build();

    static final Rules2.Endpoint TEAMS = Rules2.Endpoint.builder()
            .pathRegex("^/api/1.0/workspaces/[^/]*/teams?[^/]*")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.data[*]..name")
                    .jsonPath("$.data[*].description")
                    .jsonPath("$.data[*].html_description")
                    .build())
            .build();

    static final Rules2.Endpoint TEAM_PROJECTS = Rules2.Endpoint.builder()
            .pathRegex("^/api/1.0/teams/[^/]*/projects?[^/]*")
            .allowedQueryParams(teamsAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.data[*].current_status")
                    .jsonPath("$.data[*].current_status_update")
                    .jsonPath("$.data[*].custom_field_settings[*].custom_field.created_by")
                    .jsonPath("$.data[*].custom_field_settings[*].custom_field.description")
                    .jsonPath("$.data[*].name") //some customers consider project names sensitive
                    .jsonPath("$.data[*].notes")
                    .jsonPath("$.data[*].html_notes")
                    .jsonPath("$.data[*].created_by") // get this stuff from stories
                    .jsonPath("$.data[*].completed_by") //get this stuff from stories
                    .jsonPath("$..name") //just all names, really
                    .build())
            .build();

    static final Rules2.Endpoint LIST_TASKS = Rules2.Endpoint.builder()
            .pathRegex("^/api/1.0/tasks[?][^/]*")
            .allowedQueryParams(taskAllowedQueryParameters)
            .transforms(getTaskTransforms(true))
            .build();

    static final Rules2.Endpoint WORKSPACE_TASKS_SEARCH = Rules2.Endpoint.builder()
            .pathRegex("^/api/1.0/workspaces/[^/]*/tasks/search?[^/]*")
            .allowedQueryParams(searchTaskByWorkspaceAllowedQueryParameters)
            .transforms(getTaskTransforms(true))
            .build();

    static final Rules2.Endpoint TASK = Rules2.Endpoint.builder()
            .pathRegex("^/api/1.0/tasks/[^/]*(\\?)?[^/]*")
            .allowedQueryParams(taskAllowedQueryParameters)
            .transforms(getTaskTransforms(false))
            .build();

    static final Rules2.Endpoint TASK_STORIES = Rules2.Endpoint.builder()
            .pathRegex("^/api/1.0/tasks/[^/]*/stories?[^/]*")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$.data[*]..name") //just all names, really
                    .jsonPath("$.data[*].html_text")
                    .jsonPath("$.data[*].text")
                    .jsonPath("$.data[*].new_text_value")
                    .jsonPath("$.data[*].old_text_value")
                    .jsonPath("$.data[*].new_name")
                    .jsonPath("$.data[*].old_name")
                    .jsonPath("$.data[*].previews")
                    //TODO: story.text ever sensitive? seems like it can sometime contain important story-type context
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$.data[*].assignee.gid")
                    .jsonPath("$.data[*].created_by.gid")
                    .jsonPath("$.data[*].follower.gid")
                    .jsonPath("$.data[*].hearts[*].user.gid")
                    .jsonPath("$.data[*].likes[*].user.gid")
                    .jsonPath("$.data[*].story.created_by.gid")
                    .jsonPath("$.data[*]..email")
                    .build())
            .build();

    static final Rules2.Endpoint TASK_SUBTASKS = Rules2.Endpoint.builder()
            .pathRegex("^/api/1.0/tasks/[^/]*/subtasks?[^/]*")
            .allowedQueryParams(taskAllowedQueryParameters)
            .transforms(getTaskTransforms(true))
            .build();

    public static final RuleSet ASANA = Rules2.builder()
            .endpoint(WORKSPACES)
            .endpoint(USERS)
            .endpoint(TEAMS)
            .endpoint(TEAM_PROJECTS)
            .endpoint(LIST_TASKS)
            .endpoint(TASK)
            .endpoint(TASK_STORIES)
            .endpoint(TASK_SUBTASKS)
            .endpoint(WORKSPACE_TASKS_SEARCH)
            .build();

    private static Collection<Transform> getTaskTransforms(boolean isList) {
        String multipleExpression = isList ? "[*]" : "";
        return Lists.newArrayList(
                Transform.Redact.builder()
                        .jsonPath(String.format("$.data%s.external", multipleExpression)) //avoid random data
                        .jsonPath(String.format("$.data%s.html_notes", multipleExpression))
                        .jsonPath(String.format("$.data%s.notes", multipleExpression))
                        .jsonPath(String.format("$.data%s..name", multipleExpression)) //just all names, really
                        .jsonPath(String.format("$.data%s.custom_fields[*].description", multipleExpression))
                        .build(),
                Transform.Pseudonymize.builder()
                        .jsonPath(String.format("$.data%s.completed_by.gid", multipleExpression))
                        .jsonPath(String.format("$.data%s.hearts[*].user.gid", multipleExpression))
                        .jsonPath(String.format("$.data%s.likes[*].user.gid", multipleExpression))
                        .jsonPath(String.format("$.data%s.assignee.gid", multipleExpression))
                        .jsonPath(String.format("$.data%s.custom_fields[*].created_by.gid", multipleExpression))
                        .jsonPath(String.format("$.data%s.custom_fields[*].people_value[*].gid", multipleExpression))
                        .jsonPath(String.format("$.data%s.followers[*].gid", multipleExpression))
                        .jsonPath(String.format("$.data%s..email", multipleExpression))
                        .build());
    }
}