package co.worklytics.psoxy.rules;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class PrebuiltSanitizerRules {

    static public final Map<String, RuleSet> DEFAULTS = ImmutableMap.<String, RuleSet>builder()
        .put("asana", co.worklytics.psoxy.rules.asana.PrebuiltSanitizerRules.ASANA)
        .putAll(co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules.GOOGLE_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.msft.PrebuiltSanitizerRules.MSFT_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.slack.PrebuiltSanitizerRules.SLACK_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.zoom.PrebuiltSanitizerRules.ZOOM_PREBUILT_RULES_MAP)
        .build();
}
