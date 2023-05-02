package co.worklytics.psoxy.rules.jira;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.Lists;

import java.util.List;

public class PrebuiltSanitizerRules {

    private static final List<String> commonAllowedQueryParameters = Lists.newArrayList(
            "startAt",
            "maxResults"
    );

    static final Endpoint USERS = Endpoint.builder()
            .pathRegex("^rest/api/3/user?[?]?[^/]*")
            .allowedQueryParams(commonAllowedQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$..self")
                    .jsonPath("$..avatarUrls")
                    .jsonPath("$..displayName")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..accountId")
                    .jsonPath("$..emailAddress")
                    .build())
            .build();

    public static final RESTRules JIRA = Rules2.builder()
            .endpoint(USERS)
            .build();
}