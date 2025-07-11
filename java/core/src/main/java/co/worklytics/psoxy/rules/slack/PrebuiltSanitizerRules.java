package co.worklytics.psoxy.rules.slack;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;


import java.util.Map;

/**
 * Prebuilt sanitization rules for Slack Discovery API responses
 */
public class PrebuiltSanitizerRules {


    static final RESTRules SLACK = Rules2.load("sources/slack/slack-discovery-api/discovery.yaml");
    static final RESTRules SLACK_ANALYTICS = Rules2.load("sources/slack/slack-analytics/slack-analytics.yaml");

    static public final Map<String, RESTRules> SLACK_DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("slack", SLACK)
            .put("slack-analytics", SLACK_ANALYTICS)
            .build();
}
