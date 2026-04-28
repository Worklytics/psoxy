package co.worklytics.psoxy.rules.atlassian.organization;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Atlassian Organization Admin API responses
 */
public class PrebuiltSanitizerRules {

    static final RESTRules ORGANIZATION = Rules2.load("sources/atlassian/organization/organization.yaml");

    public static final Map<String, RESTRules> RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("atlassian-organization", ORGANIZATION)
            .build();
}

