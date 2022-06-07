package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules1;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class PrebuiltSanitizerRules {

    static public final Map<String, Rules1> DEFAULTS = ImmutableMap.<String, Rules1>builder()
        .putAll(co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules.GOOGLE_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.slack.PrebuiltSanitizerRules.SLACK_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.msft.PrebuiltSanitizerRules.MSFT_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.zoom.PrebuiltSanitizerRules.ZOOM_PREBUILT_RULES_MAP)
        .build();
}
