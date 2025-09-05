package co.worklytics.psoxy.rules.atlassian.confluence;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Confluence REST API responses
 */
public class PrebuiltSanitizerRules {

    static final RESTRules CONFLUENCE = Rules2.load("sources/atlassian/confluence/confluence.yaml");

    static public final Map<String, RESTRules> DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("confluence", CONFLUENCE)
            .build();
}
