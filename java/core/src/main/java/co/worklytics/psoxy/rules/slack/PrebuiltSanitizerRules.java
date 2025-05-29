package co.worklytics.psoxy.rules.slack;

import co.worklytics.psoxy.rules.RESTRules;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.Endpoint;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.transforms.Transform;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Prebuilt sanitization rules for Slack Discovery API responses
 */
public class PrebuiltSanitizerRules {


    static final RESTRules SLACK = Rules2.load("sources/slack/slack-discovery-api/discovery.yaml");

    static public final Map<String, RESTRules> SLACK_DEFAULT_RULES_MAP =
            ImmutableMap.<String, RESTRules>builder()
                    .put("slack", SLACK)
                    .build();
}
