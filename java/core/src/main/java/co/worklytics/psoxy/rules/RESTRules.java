package co.worklytics.psoxy.rules;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;

public interface RESTRules extends RuleSet, Serializable {

    Boolean getAllowAllEndpoints();
    List<Endpoint> getEndpoints();

    /**
     * headers allowed to be passed through to the source API.
     * <p>
     * this list is additive to any headers allowed at the endpoint level.
     */
    List<String> getAllowedRequestHeaders();

    /**
     * root definitions, to be in scope across all endpoints
     */
    Map<String, JsonSchemaFilter> getDefinitions();


}
