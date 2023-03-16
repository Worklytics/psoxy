package co.worklytics.psoxy.rules.salesforce;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.SchemaRuleUtils;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;

public class PrebuiltSanitizerRules {

    private static final String VERSION_REGEX = "v(\\d*.\\d{1})";
    private static final List<String> intervalQueryParameters = Lists.newArrayList(
            "start",
            "end"
    );

    private static final List<String> getQueryParameters = Lists.newArrayList("ids", "fields");

    private static final List<Transform> USER_TRANSFORMATIONS = Lists.newArrayList(Transform.Redact.builder()
                    .jsonPath("$..Alias")
                    .jsonPath("$..Email")
                    .jsonPath("$..Name")
                    .jsonPath("$..Username")
                    .build(),
            Transform.Pseudonymize.builder()
                    .jsonPath("$..ContactId")
                    .jsonPath("$..CreatedById")
                    .jsonPath("$..ManagerId")
                    .jsonPath("$..Id")
                    .build());

    private static final Transform ATTRIBUTES_REDACT = Transform.Redact.builder()
            .jsonPath("$..attributes")
            .build();

    private static final List<Transform> QUERY_ID_USER_TRANSFORMATION = Lists.newArrayList(Transform.Redact.builder()
                    .jsonPath("$..records[*].attributes")
                    .build(),
            Transform.Pseudonymize.builder()
                    .includeReversible(true)
                    .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                    .jsonPath("$..records[*].Id")
                    .build());

    private static SchemaRuleUtils.JsonSchema ID_QUERY_RESULT_JSON_SCHEMA = SchemaRuleUtils.JsonSchema.builder()
            .type("object")
                .properties(Map.of(
            "Id", SchemaRuleUtils.JsonSchema.builder()
                                .type("string")
                                .build()))
            .build();

    static final Endpoint DESCRIBE_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/sobjects/(Account|ActivityHistory|User)/describe$")
            // No redaction/pseudonymization, response is just metadata of the object
            .build();

    static final Endpoint UPDATED_ACCOUNTS_AND_ACTIVITY_HISTORY_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/sobjects/(Account|ActivityHistory)/updated[?][^/]*")
            .allowedQueryParams(intervalQueryParameters)
            .build();

    static final Endpoint GET_ACCOUNTS_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/composite/sobjects/Account[?][^/]*")
            .allowedQueryParams(getQueryParameters)
            .transform(ATTRIBUTES_REDACT)
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..CreatedById")
                    .jsonPath("$..LastModifiedById")
                    .jsonPath("$..OwnerId")
                    .build()
            )
            .build();

    static final Endpoint GET_USERS_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/composite/sobjects/User[?][^/]*")
            .allowedQueryParams(getQueryParameters)
            .transform(ATTRIBUTES_REDACT)
            .transforms(USER_TRANSFORMATIONS)
            .build();

    static final Endpoint GET_USERS_WITH_PSEODONYMIZED_ID_PARAMETER_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/composite/sobjects/User[?]id=(/p~[a-zA-Z0-9_-])[^/]*")
            .transform(ATTRIBUTES_REDACT)
            .transforms(USER_TRANSFORMATIONS)
            .build();

    static final Rules2 QUERY_ID_FOR_ACCOUNTS_RULES = Rules2.builder()
            .endpoint(Endpoint.builder()
                    .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT%20Id%20FROM%20Account.*$")
                    .transform(ATTRIBUTES_REDACT)
                    .responseSchema(jsonSchemaForQueryResult(ID_QUERY_RESULT_JSON_SCHEMA))
                    .build())
            .endpoint(Endpoint.builder()
                    .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT\\+Id\\+FROM\\+Account.*$")
                    .transform(ATTRIBUTES_REDACT)
                    .responseSchema(jsonSchemaForQueryResult(ID_QUERY_RESULT_JSON_SCHEMA))
                    .build())
            .build();

    static final Rules2 QUERY_FOR_ACTIVITY_HISTORIES_RULES = Rules2.builder()
            .endpoint(Endpoint.builder()
                    .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT.*FROM%20ActivityHistories.*$")
                    .transform(Transform.Pseudonymize.builder()
                            .jsonPath("$..records[*].ActivityHistories.records[*].CreatedById")
                            .jsonPath("$..records[*].ActivityHistories.records[*].LastModifiedById")
                            .jsonPath("$..records[*].ActivityHistories.records[*].OwnerId")
                            .jsonPath("$..records[*].ActivityHistories.records[*].WhoId")
                            .build())
                    .build())
            .endpoint(Endpoint.builder()
                    .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT.*FROM\\+ActivityHistories.*$")
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
                    .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT%20Id%20FROM%20User.*$")
                    .transforms(QUERY_ID_USER_TRANSFORMATION)
                    .responseSchema(jsonSchemaForQueryResult(ID_QUERY_RESULT_JSON_SCHEMA))
                    .build())
            .endpoint(Endpoint.builder()
                    .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT\\+Id\\+FROM\\+User.*$")
                    .transforms(QUERY_ID_USER_TRANSFORMATION)
                    .responseSchema(jsonSchemaForQueryResult(ID_QUERY_RESULT_JSON_SCHEMA))
                    .build())
            .build();

    public static final RuleSet SALESFORCE = Rules2.builder()
            .endpoint(DESCRIBE_ENDPOINT)
            .endpoint(UPDATED_ACCOUNTS_AND_ACTIVITY_HISTORY_ENDPOINT)
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
            .endpoint(GET_ACCOUNTS_ENDPOINT)
            .endpoint(GET_USERS_ENDPOINT)
            .endpoint(GET_USERS_WITH_PSEODONYMIZED_ID_PARAMETER_ENDPOINT)
            .endpoints(QUERY_ID_FOR_USERS_RULES.getEndpoints())
            .endpoints(QUERY_FOR_ACTIVITY_HISTORIES_RULES.getEndpoints())
            .endpoints(QUERY_ID_FOR_ACCOUNTS_RULES.getEndpoints())
            .build();

    private static SchemaRuleUtils.JsonSchema jsonSchemaForQueryResult(SchemaRuleUtils.JsonSchema recordSchema) {
        return SchemaRuleUtils.JsonSchema.builder()
                .type("object")
                .properties(Map.of(
                        "totalSize", SchemaRuleUtils.JsonSchema.builder()
                                .type("integer")
                                .build(),
                        "done", SchemaRuleUtils.JsonSchema.builder()
                                .type("boolean")
                                .build(),
                        "records", SchemaRuleUtils.JsonSchema.builder()
                                .type("array")
                                .items(recordSchema)
                                .build()))
                .build();
    }
}