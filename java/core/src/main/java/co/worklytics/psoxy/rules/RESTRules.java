package co.worklytics.psoxy.rules;

import com.avaulta.gateway.rules.Endpoint;

import java.io.Serializable;
import java.util.List;

public interface RESTRules extends RuleSet, Serializable {

    String getDefaultScopeIdForSource();

    Boolean getAllowAllEndpoints();
    List<Endpoint> getEndpoints();

}
