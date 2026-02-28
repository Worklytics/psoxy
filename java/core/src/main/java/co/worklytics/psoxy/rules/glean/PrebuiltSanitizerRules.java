package co.worklytics.psoxy.rules.glean;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Glean responses
 */
public class PrebuiltSanitizerRules {

    static final RESTRules GLEAN = Rules2.load("sources/glean/glean.yaml");

    static public final Map<String, RESTRules> DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("glean", GLEAN)
            .build();
}
