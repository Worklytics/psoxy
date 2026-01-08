package co.worklytics.psoxy.rules.anthropic;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Claude responses
 */
public class PrebuiltSanitizerRules {


    static final RESTRules CLAUDE_CODE = Rules2.load("sources/anthropic/claude-code/claude-code.yaml");

    static public final Map<String, RESTRules> DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("claude-code", CLAUDE_CODE)
            .build();
}
