package co.worklytics.psoxy.rules;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class PrebuiltSanitizerRules {

    static public final Map<String, RESTRules> DEFAULTS = ImmutableMap.<String, RESTRules>builder()
        .putAll(co.worklytics.psoxy.rules.anthropic.PrebuiltSanitizerRules.DEFAULT_RULES_MAP)
        .put("asana", co.worklytics.psoxy.rules.asana.PrebuiltSanitizerRules.ASANA)
        .putAll(co.worklytics.psoxy.rules.atlassian.confluence.PrebuiltSanitizerRules.DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.chatgpt.PrebuiltSanitizerRules.DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.cursor.PrebuiltSanitizerRules.CURSOR_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.github.PrebuiltSanitizerRules.RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.gong.PrebuiltSanitizerRules.DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules.GOOGLE_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.atlassian.jira.PrebuiltSanitizerRules.RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.msft.PrebuiltSanitizerRules.MSFT_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.salesforce.PrebuiltSanitizerRules.DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.slack.PrebuiltSanitizerRules.SLACK_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.windsurf.PrebuiltSanitizerRules.WINDSURF_DEFAULT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.zoom.PrebuiltSanitizerRules.ZOOM_PREBUILT_RULES_MAP)
        .putAll(co.worklytics.psoxy.rules.dropbox.PrebuiltSanitizerRules.DROPBOX_PREBUILT_RULES_MAP)
        .build();
}
