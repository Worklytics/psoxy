package co.worklytics.psoxy.rules.chatgpt;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for ChatGPT responses
 */
public class PrebuiltSanitizerRules {

    static final RESTRules COMPLIANCE = Rules2.load("sources/chatgpt/compliance/chatgpt-compliance.yaml");

    static public final Map<String, RESTRules> DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("chatgpt-compliance", COMPLIANCE)
            .build();
}
