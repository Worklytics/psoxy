package co.worklytics.psoxy.rules.gong;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Gong responses
 */
public class PrebuiltSanitizerRules {

    static final RESTRules GONG_METRICS = Rules2.load("sources/gong/gong-metrics/gong-metrics.yaml");

    static public final Map<String, RESTRules> DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("gong-metrics", GONG_METRICS)
            .build();
}
