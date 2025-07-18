package co.worklytics.psoxy.rules.windsurf;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Cursor responses
 */
public class PrebuiltSanitizerRules {


    static final RESTRules WINDSURF = Rules2.load("sources/windsurf/windsurf.yaml");

    static public final Map<String, RESTRules> WINDSURF_DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("windsurf", WINDSURF)
            .build();
}
