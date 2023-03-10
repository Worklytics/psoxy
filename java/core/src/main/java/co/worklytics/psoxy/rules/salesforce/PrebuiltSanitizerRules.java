package co.worklytics.psoxy.rules.salesforce;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PrebuiltSanitizerRules {

    private static final List<String> intervalQueryParameters = Lists.newArrayList(
            "start",
            "end"
    );

    private static final List<String> getQueryParameters = Lists.newArrayList("ids", "fields");

    private static final List<String> soqlQueryParameters = Collections.singletonList("q");

    static final Endpoint ACCOUNT_DESCRIBE = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/sobjects/Account/describe$")
            // No redaction/pseudonymization, response is just metadata of the object
            .build();

    static final Endpoint ACTIVITY_HISTORY_DESCRIBE = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/sobjects/ActivityHistory/describe$")
            // No redaction/pseudonymization, response is just metadata of the object
            .build();

    static final Endpoint ACCOUNT_UPDATED = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/Account/updated[?][^/]*")
            .allowedQueryParams(intervalQueryParameters)
            .build();

    static final Endpoint ACTIVITY_HISTORY_UPDATED = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/ActivityHistory/updated[?][^/]*")
            .allowedQueryParams(intervalQueryParameters)
            .build();

    static final Endpoint GET_ACCOUNT = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/composite/sobjects/Account[?][^/]*")
            .allowedQueryParams(getQueryParameters)
            .build();

    static final Endpoint GET_ACTIVITY_HISTORY = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/composite/sobjects/ActivityHistory[?][^/]*")
            .allowedQueryParams(getQueryParameters)
            .build();

    static final Endpoint QUERY = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/query[?][^/]*")
            .allowedQueryParams(soqlQueryParameters)
            .build();

    public static final RuleSet SALESFORCE = Rules2.builder()
            .endpoint(ACCOUNT_DESCRIBE)
            .endpoint(ACTIVITY_HISTORY_DESCRIBE)
            .endpoint(GET_ACCOUNT)
            .endpoint(GET_ACTIVITY_HISTORY)
            .endpoint(ACCOUNT_UPDATED)
            .endpoint(ACTIVITY_HISTORY_UPDATED)
            .endpoint(QUERY)
            .build();

}