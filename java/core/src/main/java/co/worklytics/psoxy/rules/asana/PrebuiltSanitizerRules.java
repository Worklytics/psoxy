package co.worklytics.psoxy.rules.asana;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.Transform;

public class PrebuiltSanitizerRules {

    static final Rules2.Endpoint WORKSPACES = Rules2.Endpoint.builder()
        .pathRegex("^/api/1.0/workspaces[?]?[^/]*$")
        //no redaction/pseudonymization
        // current UX for Asana connector lets users specify workspace by name, so can't redact it;
        // and we don't expect Workspace names to be sensitive or PII.
        .build();

     static final Rules2.Endpoint USERS = Rules2.Endpoint.builder()
        .pathRegex("^/api/1.0/users[?]?[^/]*")
        .transform(Transform.Redact.builder()
            .jsonPath("$.data[*].name")
            .jsonPath("$.data[*].photo")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$.data[*].email")
            .build())
        .build();

    static final Rules2.Endpoint TEAMS = Rules2.Endpoint.builder()
        .pathRegex("^/api/1.0/workspaces/[^/]*/teams?[^/]*")
        .transform(Transform.Redact.builder()
            .jsonPath("$.data[*]..name")
            .jsonPath("$.data[*].description")
            .jsonPath("$.data[*].html_description")
            .build())
        .build();

    static final Rules2.Endpoint TEAM_PROJECTS = Rules2.Endpoint.builder()
        .pathRegex("^/api/1.0/teams/[^/]*/projects?[^/]*")
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

    static final Rules2.Endpoint PROJECT_TASKS = Rules2.Endpoint.builder()
        .pathRegex("^/api/1.0/projects/[^/]*/tasks?[^/]*")
        .transform(Transform.Redact.builder()
            .jsonPath("$.data[*].external") //avoid random data
            .jsonPath("$.data[*].html_notes")
            .jsonPath("$.data[*].notes")
            .jsonPath("$.data[*]..name") //just all names, really
            .jsonPath("$.data[*].custom_fields[*].description")
            .build())
        .build();

    static final Rules2.Endpoint TASK_STORIES = Rules2.Endpoint.builder()
        .pathRegex("^/api/1.0/tasks/[^/]*/stories?[^/]*")
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
        .build();

    public static final RuleSet ASANA = Rules2.builder()
        .endpoint(WORKSPACES)
        .endpoint(USERS)
        .endpoint(TEAMS)
        .endpoint(TEAM_PROJECTS)
        .endpoint(PROJECT_TASKS)
        .endpoint(TASK_STORIES)
        .build();
}
