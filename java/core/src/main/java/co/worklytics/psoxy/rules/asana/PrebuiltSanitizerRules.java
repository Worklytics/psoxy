package co.worklytics.psoxy.rules.asana;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.Transform;

public class PrebuiltSanitizerRules {


     static final Rules2.Endpoint USERS = Rules2.Endpoint.builder()
        .pathRegex("^/api/1.0/users/?[^/]*")
        .transform(Transform.Redact.builder()
            .jsonPath("$..name")
            .jsonPath("$..photo")
            .build())
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..email")
            .build())
        .build();

    static final Rules2.Endpoint TEAMS = Rules2.Endpoint.builder()
        .build();

    static final Rules2.Endpoint TEAM_PROJECTS = Rules2.Endpoint.builder()
        .build();

    static final Rules2.Endpoint TEAM_PROJECT_TASKS = Rules2.Endpoint.builder()
        .build();

    public static final RuleSet ASANA = Rules2.builder()
        .endpoint(USERS)
        .build();
}
