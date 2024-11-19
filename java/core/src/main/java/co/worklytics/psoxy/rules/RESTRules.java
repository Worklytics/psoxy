package co.worklytics.psoxy.rules;

import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public interface RESTRules extends RuleSet, Serializable {

    Boolean getAllowAllEndpoints();
    List<Endpoint> getEndpoints();

    /**
     * root definitions, to be in scope across all endpoints
     */
    Map<String, JsonSchemaFilter> getDefinitions();


}
