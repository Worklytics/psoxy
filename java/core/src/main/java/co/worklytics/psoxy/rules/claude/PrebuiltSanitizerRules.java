package co.worklytics.psoxy.rules.claude;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Claude responses
 */
public class PrebuiltSanitizerRules {


    static final RESTRules CLAUDE = Rules2.load("sources/claude/claude.yaml");

    static public final Map<String, RESTRules> CURSOR_DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("claude", CLAUDE)
            .build();
}
