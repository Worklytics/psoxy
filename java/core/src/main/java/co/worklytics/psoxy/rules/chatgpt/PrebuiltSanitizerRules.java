package co.worklytics.psoxy.rules.chatgpt;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for ChatGPT responses
 */
public class PrebuiltSanitizerRules {

    static final RESTRules ENTERPRISE = Rules2.load("sources/chatgpt-enterprise/chatgpt-enterprise.yaml");
    /**
     * @deprecated OpenAI is deprecating the Codex-only `/v1/analytics/codex/workspaces/{workspace_id}/usage`
     * endpoint this uses, in favor of the combined ChatGPT+Codex endpoint - see {@link #CHATGPT_ENTERPRISE_ANALYTICS}.
     */
    @Deprecated
    static final RESTRules CODEX_ENTERPRISE_ANALYTICS = Rules2.load("sources/codex-enterprise-analytics/codex-enterprise-analytics.yaml");
    static final RESTRules CHATGPT_ENTERPRISE_ANALYTICS = Rules2.load("sources/chatgpt-enterprise-analytics/chatgpt-enterprise-analytics.yaml");

    static public final Map<String, RESTRules> DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("chatgpt-enterprise", ENTERPRISE)
            .put("codex-enterprise-analytics", CODEX_ENTERPRISE_ANALYTICS)
            .put("chatgpt-enterprise-analytics", CHATGPT_ENTERPRISE_ANALYTICS)
            .build();
}
