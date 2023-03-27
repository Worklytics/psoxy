package co.worklytics.psoxy.rules.salesforce;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.SchemaRuleUtils;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.Lists;

import java.util.LinkedHashMap;
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

    private final static SchemaRuleUtils.JsonSchema ID_QUERY_RESULT_JSON_SCHEMA = SchemaRuleUtils.JsonSchema.builder()
            .type("object")
            .properties(Map.of(
                    "Id", SchemaRuleUtils.JsonSchema.builder()
                            .type("string")
                            .build()))
            .build();

    private final static SchemaRuleUtils.JsonSchema USER_BY_QUERY_RESULT_JSON_SCHEMA = SchemaRuleUtils.JsonSchema.builder()
            .type("object")
            .properties(new LinkedHashMap<String, SchemaRuleUtils.JsonSchema>() {{ //req for java8-backwards compatibility
                            put("Alias", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("AccountId", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("ContactId", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("CreatedDate", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("CreatedById", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("Email", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("EmailEncodingKey", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("Id", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("IsActive", SchemaRuleUtils.JsonSchema.builder().type("boolean").build());
                            put("LastLoginDate", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("LastModifiedDate", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("ManagerId", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("Name", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("TimeZoneSidKey", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("Username", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("UserRoleId", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                            put("UserType", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                        }}
            )
            .build();

    private final static SchemaRuleUtils.JsonSchema ACTIVITY_HISTORIES_QUERY_RESULT_SCHEMA = SchemaRuleUtils.JsonSchema.builder()
            .type("object")
            .properties(Map.of(
                    "ActivityHistories", jsonSchemaForQueryResult(SchemaRuleUtils.JsonSchema.builder()
                            .type("object")
                            .properties(new LinkedHashMap<String, SchemaRuleUtils.JsonSchema>() { //req for java8-backwards compatibility
                                {
                                    put("AccountId", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("ActivityDate", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("ActivityDateTime", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("ActivitySubtype", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("ActivityType", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("CallDurationInSeconds", SchemaRuleUtils.JsonSchema.builder().type("integer").build());
                                    put("CallType", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("CreatedDate", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("CreatedById", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("DurationInMinutes", SchemaRuleUtils.JsonSchema.builder().type("integer").build());
                                    put("EndDateTime", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("Id", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("IsAllDayEvent", SchemaRuleUtils.JsonSchema.builder().type("boolean").build());
                                    put("IsDeleted", SchemaRuleUtils.JsonSchema.builder().type("boolean").build());
                                    put("IsHighPriority", SchemaRuleUtils.JsonSchema.builder().type("boolean").build());
                                    put("IsTask", SchemaRuleUtils.JsonSchema.builder().type("boolean").build());
                                    put("LastModifiedDate", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("LastModifiedById", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("OwnerId", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("Priority", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("StartDateTime", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("Status", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("WhatId", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                    put("WhoId", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                                }
                            })
                            .build())))
            .build();

    private final static SchemaRuleUtils.JsonSchema ACCOUNT_QUERY_RESULT_SCHEMA = SchemaRuleUtils.JsonSchema.builder()
            .type("object")
        .properties(new LinkedHashMap<String, SchemaRuleUtils.JsonSchema>() { //req for java8-backwards compatibility
            {
                put("Id", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("AnnualRevenue", SchemaRuleUtils.JsonSchema.builder().type("number").build());
                put("CreatedDate", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("CreatedById", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("IsDeleted", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("LastActivityDate", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("LastModifiedDate", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("LastModifiedById", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("NumberOfEmployees", SchemaRuleUtils.JsonSchema.builder().type("integer").build());
                put("OwnerId", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("Ownership", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("ParentId", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("Rating", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("Sic", SchemaRuleUtils.JsonSchema.builder().type("string").build());
                put("Type", SchemaRuleUtils.JsonSchema.builder().type("string").build());
            }})
            .build();

    private final static Transform ACCOUNT_TRANSFORMATIONS = Transform.Pseudonymize.builder()
            .jsonPath("$..CreatedById")
            .jsonPath("$..LastModifiedById")
            .jsonPath("$..OwnerId")
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
            .transform(ACCOUNT_TRANSFORMATIONS)
            .build();

    static final Endpoint GET_USERS_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/composite/sobjects/User[?][^/]*")
            .allowedQueryParams(getQueryParameters)
            .transform(ATTRIBUTES_REDACT)
            .transforms(USER_TRANSFORMATIONS)
            .build();

    static final Endpoint QUERY_ID_FOR_ACCOUNTS_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT(%20|\\+)Id(%20|\\+)FROM(%20|\\+)Account.*$")
            .responseSchema(jsonSchemaForQueryResult(ID_QUERY_RESULT_JSON_SCHEMA))
            .build();

    static final Endpoint QUERY_USERS_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT.*FROM(%20|\\+)User(%20|\\+)WHERE(%20|\\+)LastModifiedDate.*$")
            .transforms(USER_TRANSFORMATIONS)
            .responseSchema(jsonSchemaForQueryResult(USER_BY_QUERY_RESULT_JSON_SCHEMA))
            .build();

    static final Endpoint QUERY_ACCOUNTS_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT.*FROM(%20|\\+)Account(%20|\\+)WHERE(%20|\\+)LastModifiedDate.*$")
            .transform(ACCOUNT_TRANSFORMATIONS)
            .responseSchema(jsonSchemaForQueryResult(ACCOUNT_QUERY_RESULT_SCHEMA))
            .build();

    static final Endpoint QUERY_FOR_ACTIVITY_HISTORIES_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT.*FROM(%20|\\+)ActivityHistories.*$")
            .transform(Transform.Pseudonymize.builder()
                    .jsonPath("$..records[*].ActivityHistories.records[*].CreatedById")
                    .jsonPath("$..records[*].ActivityHistories.records[*].LastModifiedById")
                    .jsonPath("$..records[*].ActivityHistories.records[*].OwnerId")
                    .jsonPath("$..records[*].ActivityHistories.records[*].WhoId")
                    .build())
            .responseSchema(jsonSchemaForQueryResult(ACTIVITY_HISTORIES_QUERY_RESULT_SCHEMA))
            .build();

    static final Endpoint QUERY_ID_FOR_USERS_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT(%20|\\+)Id(%20|\\+)FROM(%20|\\+)User.*$")
            .transforms(QUERY_ID_USER_TRANSFORMATION)
            .responseSchema(jsonSchemaForQueryResult(ID_QUERY_RESULT_JSON_SCHEMA))
            .build();

    public static final RESTRules SALESFORCE = Rules2.builder()
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
            .endpoint(QUERY_ID_FOR_USERS_ENDPOINT)
            .endpoint(QUERY_ID_FOR_ACCOUNTS_ENDPOINT)
            .endpoint(QUERY_USERS_ENDPOINT)
            .endpoint(QUERY_ACCOUNTS_ENDPOINT)
            .endpoint(QUERY_FOR_ACTIVITY_HISTORIES_ENDPOINT)
            .build();

    private static SchemaRuleUtils.JsonSchema jsonSchemaForQueryResult(SchemaRuleUtils.JsonSchema recordSchema) {
        return SchemaRuleUtils.JsonSchema.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, SchemaRuleUtils.JsonSchema>() {{ //req for java8-backwards compatibility
                    put("totalSize", SchemaRuleUtils.JsonSchema.builder()
                            .type("integer")
                            .build());
                    put("done", SchemaRuleUtils.JsonSchema.builder()
                            .type("boolean")
                            .build());
                    put("nextRecordsUrl", SchemaRuleUtils.JsonSchema.builder()
                            .type("string")
                            .build());
                    put("records", SchemaRuleUtils.JsonSchema.builder()
                            .type("array")
                            .items(recordSchema)
                            .build());
                }})
                .build();
    }
}

