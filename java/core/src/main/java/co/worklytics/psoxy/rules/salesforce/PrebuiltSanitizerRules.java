package co.worklytics.psoxy.rules.salesforce;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

public class PrebuiltSanitizerRules {

    private static final List<String> intervalQueryParameters = Lists.newArrayList(
            "start",
            "end"
    );

    private static final List<String> getQueryParameters = Lists.newArrayList("ids", "fields");

    static final Endpoint DESCRIBE = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/sobjects/(Account|ActivityHistory|User)/describe$")
            // No redaction/pseudonymization, response is just metadata of the object
            .build();

    static final Endpoint UPDATED_ACCOUNTS_AND_ACTIVITY_HISTORY = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/(Account|ActivityHistory)/updated[?][^/]*")
            .allowedQueryParams(intervalQueryParameters)
            .build();

    static final Endpoint UPDATED_USERS = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/User/updated[?][^/]*")
            .allowedQueryParams(intervalQueryParameters)
            .transform(Transform.Pseudonymize.builder()
                    .includeReversible(true)
                    .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                    .jsonPath("$..ids")
                    .build())
            .build();

    static final Endpoint GET_ACCOUNTS = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/composite/sobjects/Account[?][^/]*")
            .allowedQueryParams(getQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$..attributes")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..CreatedById")
                    .jsonPath("$..LastModifiedById")
                    .jsonPath("$..OwnerId")
                    .build()
            )
            .build();

    static final Endpoint GET_USERS = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/composite/sobjects/User[?][^/]*")
            .allowedQueryParams(getQueryParameters)
            .transform(Transform.Redact.builder()
                    .jsonPath("$..attributes")
                    .build())
            .transform(Transform.Redact.builder()
                    .jsonPath("$..Alias")
                    .jsonPath("$..Email")
                    .jsonPath("$..Name")
                    .jsonPath("$..Username.name")
                    .build()
            )
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..ContactId")
                    .jsonPath("$..CreatedById")
                    .jsonPath("$..ManagerId")
                    .build()
            )
            .transform(Transform.Pseudonymize.builder()
                    .includeReversible(true)
                    .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                    .jsonPath("$..Id")
                    .build())
            .build();

    static final Endpoint USERS_NO_IDS = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/composite/sobjects/User[?]id=(/p~[a-zA-Z0-9_-])[^/]*")
            .transform(Transform.Redact.builder()
                    .jsonPath("$..attributes")
                    .build())
            .transform(Transform.Redact.builder()
                    .jsonPath("$..Alias")
                    .jsonPath("$..Email")
                    .jsonPath("$..Name")
                    .jsonPath("$..Username.name")
                    .build()
            )
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..ContactId")
                    .jsonPath("$..CreatedById")
                    .jsonPath("$..ManagerId")
                    .build()
            )
            .transform(Transform.Pseudonymize.builder()
                    .includeReversible(true)
                    .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                    .jsonPath("$..Id")
                    .build())
            .build();

    static final Endpoint QUERY_ID_FOR_ACCOUNTS = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/query[?]q=SELECT.*[FROM|from]%20[Account|account].*$")
            .transform(Transform.Redact.builder()
                    .jsonPath("$..attributes")
                    .build())
            .build();

    static final Endpoint QUERY_FOR_ACTIVITY_HISTORIES = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/query[?]q=SELECT.*[FROM|from]%20[ActivityHistories|activityhistories].*$")
            .transform(Transform.Redact.builder()
                    .jsonPath("$..attributes")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..CreatedById")
                    .jsonPath("$..LastModifiedById")
                    .jsonPath("$..OwnerId")
                    .jsonPath("$..WhoId")
                    .build())
            .build();

    static final Endpoint QUERY_ID_FOR_USERS = Endpoint.builder()
            .pathRegex("^/services/data/v51.0/query[?]q=SELECT.*[FROM|from]%20[User|user].*$")
            .transform(Transform.Redact.builder()
                    .jsonPath("$..attributes")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .includeReversible(true)
                    .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                    .jsonPath("$..Id")
                    .build())
            .build();

    public static final RuleSet SALESFORCE = Rules2.builder()
            .endpoint(DESCRIBE)
            .endpoint(UPDATED_ACCOUNTS_AND_ACTIVITY_HISTORY)
            .endpoint(UPDATED_USERS)
            .endpoint(GET_ACCOUNTS)
            .endpoint(GET_USERS)
            .endpoint(USERS_NO_IDS)
            .endpoint(QUERY_ID_FOR_ACCOUNTS)
            .endpoint(QUERY_ID_FOR_USERS)
            .endpoint(QUERY_FOR_ACTIVITY_HISTORIES)
            .build();

}