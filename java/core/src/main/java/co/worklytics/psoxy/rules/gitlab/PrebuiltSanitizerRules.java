package co.worklytics.psoxy.rules.gitlab;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for GitLab responses
 */
public class PrebuiltSanitizerRules {


    static final RESTRules GITLAB_CLOUD = Rules2.load("sources/gitlab/gitlab-cloud/gitlab-cloud.yaml");
    static final RESTRules GITLAB_MANAGED = Rules2.load("sources/gitlab/gitlab-managed/gitlab-managed.yaml");

    static public final Map<String, RESTRules> DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("gitlab-cloud", GITLAB_CLOUD)
            .put("gitlab-managed", GITLAB_MANAGED)
            .build();
}
