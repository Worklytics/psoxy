package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static co.worklytics.psoxy.Rules.Rule;

public class PrebuiltSanitizerRules {

    static public final Map<String, Rules> MAP = ImmutableMap.<String, Rules>builder()
        .putAll(co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules.GOOGLE_PREBUILT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.slack.PrebuiltSanitizerRules.SLACK_PREBUILT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.msft.PrebuiltSanitzerRules.MSFT_PREBUILT_RULES_MAP)
        .build();
}
