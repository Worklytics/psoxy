package co.worklytics.psoxy.rules.salesforce;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.Endpoint;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

public class PrebuiltSanitizerRules {

    private static final List<String> intervalQueryParameters = Lists.newArrayList(
            "start",
            "end"
    );

    private static final List<String> getQueryParameters = Lists.newArrayList("ids", "fields");

    private static final List<String> soqlQueryParameters = Collections.singletonList("q");

    static final Endpoint DESCRIBE = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/sobjects/(Account|ActivityHistory|User)/describe$")
            // No redaction/pseudonymization, response is just metadata of the object
            .build();

    static final Endpoint UPDATED = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/(Account|ActivityHistory|User)/updated[?][^/]*")
            .allowedQueryParams(intervalQueryParameters)
            .build();

    static final Endpoint GET = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/composite/sobjects/(Account|ActivityHistory|User)[?][^/]*")
            .allowedQueryParams(getQueryParameters)
            .build();

    static final Endpoint QUERY = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/query[?][^/]*")
            .allowedQueryParams(soqlQueryParameters)
            .build();

    public static final RuleSet SALESFORCE = Rules2.builder()
            .endpoint(DESCRIBE)
            .endpoint(GET)
            .endpoint(UPDATED)
            .endpoint(QUERY)
            .build();

}