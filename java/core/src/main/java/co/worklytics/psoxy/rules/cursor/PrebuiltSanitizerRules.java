package co.worklytics.psoxy.rules.cursor;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Cursor responses
 */
public class PrebuiltSanitizerRules {


    static final RESTRules CURSOR = Rules2.load("sources/cursor/cursor.yaml");

    static public final Map<String, RESTRules> CURSOR_DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("cursor", CURSOR)
            .build();
}
