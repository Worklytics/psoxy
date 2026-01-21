package co.worklytics.psoxy.rules.zoom;

import com.avaulta.gateway.rules.Endpoint;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.transforms.Transform;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Zoom API responses
 */
public class PrebuiltSanitizerRules {
    static final Rules2 ZOOM = Rules2.load("sources/zoom/zoom.yaml");

    static public final Map<String, RESTRules> ZOOM_PREBUILT_RULES_MAP = ImmutableMap.<String, RESTRules>builder()
        .put("zoom", ZOOM)
        .build();
}
