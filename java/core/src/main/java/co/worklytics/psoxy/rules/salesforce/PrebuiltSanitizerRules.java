package co.worklytics.psoxy.rules.salesforce;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.JsonSchemaFilterUtils;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.*;

public class PrebuiltSanitizerRules {

    static final RESTRules SALESFORCE = Rules2.load("sources/salesforce/salesforce.yaml");

    static public final Map<String, RESTRules> DEFAULT_RULES_MAP =
        ImmutableMap.<String, RESTRules>builder()
            .put("salesforce", SALESFORCE)
            .build();
}
