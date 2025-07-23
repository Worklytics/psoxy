package co.worklytics.psoxy.rules.salesforce;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.JsonSchemaFilterUtils;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.Lists;

import java.util.*;

public class PrebuiltSanitizerRules {

    private static final String VERSION_REGEX = "v(\\d*.\\d{1})";
    private static final List<String> intervalQueryParameters = Lists.newArrayList(
            "start",
            "end"
    );

    private static final List<String> getQueryParameters = Lists.newArrayList("ids", "fields");

    private static final Transform ATTRIBUTES_REDACT = Transform.Redact.builder()
            .jsonPath("$..attributes")
            .build();

    private final static Map<String, JsonSchemaFilter> USER_BY_QUERY_RESULT_JSON_SCHEMA = new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
        put("attributes", buildAttributeWithType());

        put("Alias", JsonSchemaFilter.builder().type("string").build());
        put("AccountId", JsonSchemaFilter.builder().type("string").build());
        put("ContactId", JsonSchemaFilter.builder().type("string").build());
        put("CreatedDate", JsonSchemaFilter.builder().type("string").build());
        put("CreatedById", JsonSchemaFilter.builder().type("string").build());
        put("Email", JsonSchemaFilter.builder().type("string").build());
        put("EmailEncodingKey", JsonSchemaFilter.builder().type("string").build());
        put("Id", JsonSchemaFilter.builder().type("string").build());
        put("IsActive", JsonSchemaFilter.builder().type("boolean").build());
        put("LastLoginDate", JsonSchemaFilter.builder().type("string").build());
        put("LastModifiedDate", JsonSchemaFilter.builder().type("string").build());
        put("ManagerId", JsonSchemaFilter.builder().type("string").build());
        put("Name", JsonSchemaFilter.builder().type("string").build());
        put("TimeZoneSidKey", JsonSchemaFilter.builder().type("string").build());
        put("Username", JsonSchemaFilter.builder().type("string").build());
        put("UserRoleId", JsonSchemaFilter.builder().type("string").build());
        put("UserType", JsonSchemaFilter.builder().type("string").build());
    }};

    private final static Map<String, JsonSchemaFilter> ACTIVITY_HISTORIES_RESULT_PROPERTY_SCHEMA = new LinkedHashMap<String, JsonSchemaFilter>() { //req for java8-backwards compatibility
        {
            put("attributes", buildAttributeWithType());

            put("AccountId", JsonSchemaFilter.builder().type("string").build());
            put("ActivityDate", JsonSchemaFilter.builder().type("string").build());
            put("ActivityDateTime", JsonSchemaFilter.builder().type("string").build());
            put("ActivitySubtype", JsonSchemaFilter.builder().type("string").build());
            put("ActivityType", JsonSchemaFilter.builder().type("string").build());
            put("CallDurationInSeconds", JsonSchemaFilter.builder().type("integer").build());
            put("CallType", JsonSchemaFilter.builder().type("string").build());
            put("CreatedDate", JsonSchemaFilter.builder().type("string").build());
            put("CreatedById", JsonSchemaFilter.builder().type("string").build());
            put("DurationInMinutes", JsonSchemaFilter.builder().type("integer").build());
            put("EndDateTime", JsonSchemaFilter.builder().type("string").build());
            put("Id", JsonSchemaFilter.builder().type("string").build());
            put("IsAllDayEvent", JsonSchemaFilter.builder().type("boolean").build());
            put("IsDeleted", JsonSchemaFilter.builder().type("boolean").build());
            put("IsHighPriority", JsonSchemaFilter.builder().type("boolean").build());
            put("IsTask", JsonSchemaFilter.builder().type("boolean").build());
            put("LastModifiedDate", JsonSchemaFilter.builder().type("string").build());
            put("LastModifiedById", JsonSchemaFilter.builder().type("string").build());
            put("OwnerId", JsonSchemaFilter.builder().type("string").build());
            put("Priority", JsonSchemaFilter.builder().type("string").build());
            put("StartDateTime", JsonSchemaFilter.builder().type("string").build());
            put("Status", JsonSchemaFilter.builder().type("string").build());
            put("WhatId", JsonSchemaFilter.builder().type("string").build());
            put("WhoId", JsonSchemaFilter.builder().type("string").build());
        }
    };

    private final static Map<String, JsonSchemaFilter> ACCOUNT_WITH_ACTIVITY_HISTORIES_QUERY_RESULT_PROPERTY_SCHEMA = new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
        put("attributes", buildAttributeWithType());

        put("Id", JsonSchemaFilter.builder().type("string").build());
        put("AnnualRevenue", JsonSchemaFilter.builder().type("number").build());
        put("CreatedDate", JsonSchemaFilter.builder().type("string").build());
        put("CreatedById", JsonSchemaFilter.builder().type("string").build());
        put("IsDeleted", JsonSchemaFilter.builder().type("string").build());
        put("LastActivityDate", JsonSchemaFilter.builder().type("string").build());
        put("LastModifiedDate", JsonSchemaFilter.builder().type("string").build());
        put("LastModifiedById", JsonSchemaFilter.builder().type("string").build());
        put("NumberOfEmployees", JsonSchemaFilter.builder().type("integer").build());
        put("OwnerId", JsonSchemaFilter.builder().type("string").build());
        put("Ownership", JsonSchemaFilter.builder().type("string").build());
        put("ParentId", JsonSchemaFilter.builder().type("string").build());
        put("Rating", JsonSchemaFilter.builder().type("string").build());
        put("Sic", JsonSchemaFilter.builder().type("string").build());
        put("Type", JsonSchemaFilter.builder().type("string").build());

        put("ActivityHistories", JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility

                    put("totalSize", JsonSchemaFilter.builder()
                            .type("integer")
                            .build());
                    put("done", JsonSchemaFilter.builder()
                            .type("boolean")
                            .build());
                    put("nextRecordsUrl", JsonSchemaFilter.builder()
                            .type("string")
                            .build());
                    put("records", JsonSchemaFilter.builder()
                            .type("array")
                            .items(JsonSchemaFilter.builder()
                                    .type("object")
                                    .properties(Collections.emptyMap())
                                    ._if(JsonSchemaFilterUtils.ConditionJsonSchema.builder().properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                        put("attributes", JsonSchemaFilter.builder()
                                                .type("object")
                                                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                                    put("type", JsonSchemaFilter.builder()
                                                            .type("string")
                                                            .constant("ActivityHistory")
                                                            .build());
                                                }})
                                                .build());
                                    }}).build())
                                    ._then(JsonSchemaFilterUtils.ThenJsonSchema.builder().properties(ACTIVITY_HISTORIES_RESULT_PROPERTY_SCHEMA).build())
                                    .build())
                            .build());
                }}).build());
    }};

    private final static Map<String, JsonSchemaFilter> ACTIVITY_HISTORIES_SUB_QUERY_RESULT_PROPERTY_SCHEMA = new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
        put("attributes", buildAttributeWithType());
        put("ActivityHistories", JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility

                    put("totalSize", JsonSchemaFilter.builder()
                            .type("integer")
                            .build());
                    put("done", JsonSchemaFilter.builder()
                            .type("boolean")
                            .build());
                    put("nextRecordsUrl", JsonSchemaFilter.builder()
                            .type("string")
                            .build());
                    put("records", JsonSchemaFilter.builder()
                            .type("array")
                            .items(JsonSchemaFilter.builder()
                                    .type("object")
                                    .properties(Collections.emptyMap())
                                    ._if(JsonSchemaFilterUtils.ConditionJsonSchema.builder().properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                        put("attributes", JsonSchemaFilter.builder()
                                                .type("object")
                                                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                                    put("type", JsonSchemaFilter.builder()
                                                            .type("string")
                                                            .constant("ActivityHistory")
                                                            .build());
                                                }})
                                                .build());
                                    }}).build())
                                    ._then(JsonSchemaFilterUtils.ThenJsonSchema.builder().properties(ACTIVITY_HISTORIES_RESULT_PROPERTY_SCHEMA).build())
                                    .build())
                            .build());
                }}).build());
    }};

    private static final List<String> DEFAULT_QUERY_HEADERS = Arrays.asList( "Sforce-Query-Options");

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
            .transform(getAccountTransformations(".",false))
            .build();

    static final Endpoint GET_USERS_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/composite/sobjects/User[?][^/]*")
            .allowedQueryParams(getQueryParameters)
            .transform(ATTRIBUTES_REDACT)
            .transforms(getUserTransformations(".", false))
            .build();

    static final Endpoint QUERY_USERS_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT.*FROM(%20|\\+)User(%20|\\+)WHERE(%20|\\+)LastModifiedDate.*$")
            .allowedRequestHeadersToForward(DEFAULT_QUERY_HEADERS)
            .transforms(getUserTransformations(".records[*]", true))
            .responseSchema(jsonSchemaForQueryResult("User", USER_BY_QUERY_RESULT_JSON_SCHEMA))
            .build();

    static final Endpoint QUERY_ACCOUNTS_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT.*FROM(%20|\\+)Account(%20|\\+)WHERE(%20|\\+)LastModifiedDate.*$")
            .allowedRequestHeadersToForward(DEFAULT_QUERY_HEADERS)
            .transform(getAccountTransformations(".records[*]", true))
            .responseSchema(jsonSchemaForQueryResult("Account", ACCOUNT_WITH_ACTIVITY_HISTORIES_QUERY_RESULT_PROPERTY_SCHEMA))
            .build();

    static final Endpoint QUERY_FOR_ACTIVITY_HISTORIES_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/query[?]q=SELECT.*FROM(%20|\\+)ActivityHistories.*$")
            .allowedRequestHeadersToForward(DEFAULT_QUERY_HEADERS)
            .transform(getActivityHistoryTransformations(false))
            .responseSchema(jsonSchemaForActivityHistoryQueryResult())
            .build();

    static final Endpoint QUERY_PAGINATION_ENDPOINT = Endpoint.builder()
            .pathRegex("^/services/data/" + VERSION_REGEX + "/query/.*")
            .allowedRequestHeadersToForward(DEFAULT_QUERY_HEADERS)
            .transforms(getUserTransformations(".records[*]", true))
            .transform(getAccountTransformations(".records[*]", true))
            .transform(getActivityHistoryTransformations(true))
            .responseSchema(jsonSchemaForQueryResult())
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
            .endpoint(QUERY_USERS_ENDPOINT)
            .endpoint(QUERY_ACCOUNTS_ENDPOINT)
            .endpoint(QUERY_FOR_ACTIVITY_HISTORIES_ENDPOINT)
            .endpoint(QUERY_PAGINATION_ENDPOINT)
            .build();

    private static JsonSchemaFilter jsonSchemaForQueryResult(String recordType, Map<String, JsonSchemaFilter> responsePropertyMap) {
        return JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility

                    put("totalSize", JsonSchemaFilter.builder()
                            .type("integer")
                            .build());
                    put("done", JsonSchemaFilter.builder()
                            .type("boolean")
                            .build());
                    put("nextRecordsUrl", JsonSchemaFilter.builder()
                            .type("string")
                            .build());
                    put("records", JsonSchemaFilter.builder()
                            .type("array")
                            .items(JsonSchemaFilter.builder()
                                    .type("object")
                                    .properties(Collections.emptyMap())
                                    ._if(JsonSchemaFilterUtils.ConditionJsonSchema.builder().properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                        put("attributes", buildAttributeWithType(recordType));
                                    }}).build())
                                    ._then(JsonSchemaFilterUtils.ThenJsonSchema.builder().properties(responsePropertyMap).build())
                                    .build())
                            .build());
                }})
                .build();
    }

    private static JsonSchemaFilter jsonSchemaForActivityHistoryQueryResult() {
        return JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility

                    put("totalSize", JsonSchemaFilter.builder()
                            .type("integer")
                            .build());
                    put("done", JsonSchemaFilter.builder()
                            .type("boolean")
                            .build());
                    put("nextRecordsUrl", JsonSchemaFilter.builder()
                            .type("string")
                            .build());
                    put("records", JsonSchemaFilter.builder()
                            .type("array")
                            .items(JsonSchemaFilter.builder()
                                    .type("object")
                                    .properties(Collections.emptyMap())
                                    ._if(JsonSchemaFilterUtils.ConditionJsonSchema.builder().properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                        put("attributes", buildAttributeWithType("Account"));
                                    }}).build())
                                    ._then(JsonSchemaFilterUtils.ThenJsonSchema.builder().properties(ACTIVITY_HISTORIES_SUB_QUERY_RESULT_PROPERTY_SCHEMA).build())
                                    .build())
                            .build());
                }})
                .build();
    }

    private static JsonSchemaFilter jsonSchemaForQueryResult() {
        return JsonSchemaFilter.builder()
                .type("object")
                // Using LinkedHashMap to keep the order to support same
                // YAML serialization result
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility

                    put("totalSize", JsonSchemaFilter.builder()
                            .type("integer")
                            .build());
                    put("done", JsonSchemaFilter.builder()
                            .type("boolean")
                            .build());
                    put("nextRecordsUrl", JsonSchemaFilter.builder()
                            .type("string")
                            .build());
                    put("records", JsonSchemaFilter.builder()
                            .type("array")
                            .items(JsonSchemaFilter.builder()
                                    .anyOf(Arrays.asList(
                                                    JsonSchemaFilter.builder().type("object")
                                                            .properties(Collections.emptyMap())._if(JsonSchemaFilterUtils.ConditionJsonSchema.builder().properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                                                put("attributes", buildAttributeWithType("User"));
                                                            }}).build())
                                                            ._then(JsonSchemaFilterUtils.ThenJsonSchema.builder().properties(USER_BY_QUERY_RESULT_JSON_SCHEMA).build())
                                                            .build(),
                                                    JsonSchemaFilter.builder().type("object").properties(Collections.emptyMap())._if(JsonSchemaFilterUtils.ConditionJsonSchema.builder().properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                                                                put("attributes", buildAttributeWithType("Account"));
                                                            }}).build())
                                                            ._then(JsonSchemaFilterUtils.ThenJsonSchema.builder().properties(ACCOUNT_WITH_ACTIVITY_HISTORIES_QUERY_RESULT_PROPERTY_SCHEMA).build())
                                                            .build())
                                    ).build())
                            .build());
                }})
                .build();
    }

    private static Transform getAccountTransformations(String pathPrefix,boolean applyOnlyWhen) {
        return Transform.Pseudonymize.builder()
                .jsonPath(String.format("$%s.CreatedById", pathPrefix))
                .jsonPath(String.format("$%s.LastModifiedById", pathPrefix))
                .jsonPath(String.format("$%s.OwnerId", pathPrefix))
                .applyOnlyWhen(applyOnlyWhen ? "$.records[?(@.attributes.type == \"Account\")]" : null)
                .build();
    }

    private static List<Transform> getUserTransformations(String pathPrefix, boolean applyOnlyWhen) {
        return Lists.newArrayList(Transform.Redact.builder()
                        .jsonPath(String.format("$%s.Alias", pathPrefix))
                        .jsonPath(String.format("$%s.Name", pathPrefix))
                        .jsonPath(String.format("$%s.Username", pathPrefix))
                        .applyOnlyWhen(applyOnlyWhen ? "records[?(@.attributes.type == \"User\")]" : null)
                        .build(),
                Transform.Pseudonymize.builder()
                        .jsonPath(String.format("$%s.ContactId", pathPrefix))
                        .jsonPath(String.format("$%s.CreatedById", pathPrefix))
                        .jsonPath(String.format("$%s.ManagerId", pathPrefix))
                        .jsonPath(String.format("$%s.Email", pathPrefix))
                        .jsonPath(String.format("$%s.Id", pathPrefix))
                        .applyOnlyWhen(applyOnlyWhen ? "$.records[?(@.attributes.type == \"User\")]" : null)
                        .build());
    }

    private static Transform.Pseudonymize getActivityHistoryTransformations(boolean applyOnlyWhen) {
        return Transform.Pseudonymize.builder()
                .jsonPath("$.records[*].ActivityHistories.records[*].CreatedById")
                .jsonPath("$.records[*].ActivityHistories.records[*].LastModifiedById")
                .jsonPath("$.records[*].ActivityHistories.records[*].OwnerId")
                .jsonPath("$.records[*].ActivityHistories.records[*].WhoId")
                .applyOnlyWhen(applyOnlyWhen ? "$.records[*].ActivityHistories.records[?(@.attributes.type == \"ActivityHistory\")]" : null)
                .build();
    }

    private static JsonSchemaFilter buildAttributeWithType() {
        return buildAttributeWithType(null);
    }

    private static JsonSchemaFilter buildAttributeWithType(String constant) {
        return JsonSchemaFilter.builder()
                .type("object")
                .properties(new LinkedHashMap<String, JsonSchemaFilter>() {{ //req for java8-backwards compatibility
                    put("type", JsonSchemaFilter.builder()
                            .type("string")
                            .constant(constant)
                            .build());
                }})
                .build();
    }
}
