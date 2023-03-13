package co.worklytics.psoxy.rules.salesforce;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.Lists;

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
            .pathRegex("^/services/data/v51.0/sobjects/(Account|ActivityHistory)/updated[?][^/]*")
            .allowedQueryParams(intervalQueryParameters)
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
                    .jsonPath("$..Username")
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
                    .jsonPath("$..Username")
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

    static final Rules2 QUERY_ID_FOR_ACCOUNTS_RULES = Rules2.builder()
            .endpoint(Endpoint.builder()
            .pathRegex("^/services/data/v51.0/query[?]q=SELECT%20Id%20FROM%20Account.*$")
            .transform(Transform.Redact.builder()
                    .jsonPath("$..attributes")
                    .build())
            .build())
            .endpoint(Endpoint.builder()
                    .pathRegex("^/services/data/v51.0/query[?]q=SELECT\\+Id\\+FROM\\+Account.*$")
                    .transform(Transform.Redact.builder()
                            .jsonPath("$..attributes")
                            .build())
                    .build())
            .build();

    static final Rules2 QUERY_FOR_ACTIVITY_HISTORIES_RULES = Rules2.builder()
            .endpoint(Endpoint.builder()
            .pathRegex("^/services/data/v51.0/query[?]q=SELECT.*FROM%20ActivityHistories.*$")
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..records[*].ActivityHistories.records[*].CreatedById")
                    .jsonPath("$..records[*].ActivityHistories.records[*].LastModifiedById")
                    .jsonPath("$..records[*].ActivityHistories.records[*].OwnerId")
                    .jsonPath("$..records[*].ActivityHistories.records[*].WhoId")
                    .build())
            .build())
            .endpoint(Endpoint.builder()
                    .pathRegex("^/services/data/v51.0/query[?]q=SELECT.*FROM+ActivityHistories.*$")
                    .transform(Transform.Pseudonymize.builder()
                            .jsonPath("$..records[*].ActivityHistories.records[*].CreatedById")
                            .jsonPath("$..records[*].ActivityHistories.records[*].LastModifiedById")
                            .jsonPath("$..records[*].ActivityHistories.records[*].OwnerId")
                            .jsonPath("$..records[*].ActivityHistories.records[*].WhoId")
                            .build())
                    .build())
            .build();

    static final Rules2 QUERY_ID_FOR_USERS_RULES = Rules2.builder()
            .endpoint(Endpoint.builder()
            .pathRegex("^/services/data/v51.0/query[?]q=SELECT%20Id%20FROM%20User.*$")
            .transform(Transform.Redact.builder()
                    .jsonPath("$..records[*].attributes")
                    .build())
            .transform(Transform.Pseudonymize.builder()
                    .includeReversible(true)
                    .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                    .jsonPath("$..records[*].Id")
                    .build())
            .build())
            .endpoint(Endpoint.builder()
                    .pathRegex("^/services/data/v51.0/query[?]q=SELECT\\+Id\\+FROM\\+User.*$")
                    .transform(Transform.Redact.builder()
                            .jsonPath("$..records[*].attributes")
                            .build())
                    .transform(Transform.Pseudonymize.builder()
                            .includeReversible(true)
                            .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                            .jsonPath("$..records[*].Id")
                            .build())
                    .build())
            .build();

    public static final RuleSet SALESFORCE = Rules2.builder()
            .endpoint(DESCRIBE)
            .endpoint(UPDATED_ACCOUNTS_AND_ACTIVITY_HISTORY)
            // Note: Update users is not used, as it will return an array
            // of ids which needs to be pseudonymized but is not compatible with
            // how pseudonymization works. See example below.
            //
            // For this, that endpoint is not being supported
            //
            // Example:
            // {
            //  "ids": [
            //    "0015Y00002ZbgP3QAJ",
            //    "0015Y00002c7g8uQAA"
            //  ],
            //  "latestDateCovered": "2023-03-09T18:44:00.000+0000"
            //}
            .endpoint(GET_ACCOUNTS)
            .endpoint(GET_USERS)
            .endpoint(USERS_NO_IDS)
            .endpoints(QUERY_ID_FOR_USERS_RULES.getEndpoints())
            .endpoints(QUERY_FOR_ACTIVITY_HISTORIES_RULES.getEndpoints())
            .endpoints(QUERY_ID_FOR_ACCOUNTS_RULES.getEndpoints())
            .build();

}