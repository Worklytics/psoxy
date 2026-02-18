package co.worklytics.psoxy.rules.gitlab;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Cursor responses
 */
public class PrebuiltSanitizerRules {


    static final RESTRules GITLAB = Rules2.load("sources/gitlab/gitlab.yaml");

    static public final Map<String, RESTRules> DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("gitlab", GITLAB)
            .build();
}
