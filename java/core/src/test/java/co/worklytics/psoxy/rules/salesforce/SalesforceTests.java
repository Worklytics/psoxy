package co.worklytics.psoxy.rules.salesforce;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.salesforce.PrebuiltSanitizerRules;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class SalesforceTests extends JavaRulesTestBaseCase {

    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.SALESFORCE;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceKind("salesforce")
        .build();

    @Disabled // not reliable; seems to have different value via IntelliJ/AWS deployment and my
    // laptop's maven, which doesn't make any sense, given that binary deployed to AWS was built via
    // maven on my laptop - so something about Surefire/Junit runner used by maven??
    @Test
    void sha() {
        this.assertSha("7869e465607b7a00b4bd75a832a9ed1f811ce7f2");
    }

    @Test
    @Override
    @Disabled
    public void yamlLength() {
        // Do nothing, as response schema is bigger than we allow for advanced parameters
    }

    @Test
    void account_describe() {
        String jsonString = asJson("account_describe.json");

        String endpoint = "https://test.salesforce.com/services/data/v51.0/sobjects/Account/describe";

        assertUrlWithQueryParamsBlocked(endpoint);
        assertUrlWithSubResourcesBlocked(endpoint);

        this.sanitize(endpoint, jsonString);
    }

    @Test
    void accounts_by_id() {
        String jsonString = asJson("accounts.json");

        String endpoint = "https://test.salesforce.com/services/data/v51.0/composite/sobjects/Account?ids=0015Y00002c7g95QAA,0015Y00002c7g8uQAA&fields=Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type";

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "0055Y00000E16gwQAB");
        assertPseudonymized(sanitized, "0055Y00000E18EHQAZ");
        assertPseudonymized(sanitized, "0055Y00000E16gwQAB");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT+Alias,AccountId,ContactId,CreatedDate,CreatedById,Email,EmailEncodingKey,Id,IsActive,LastLoginDate,LastModifiedDate,ManagerId,Name,TimeZoneSidKey,Username,UserRoleId,UserType+FROM+User+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST",
    })
    void query_users(String query) {
        String jsonString = asJson("users_by_query.json");

        String endpoint = "https://test.salesforce.com/services/data/v51.0/query?q=" + query;

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "0055Y00000ExkfuQAB");
        assertPseudonymized(sanitized, "0055Y00000E16gwQAB");
        assertPseudonymized(sanitized, "0055Y00000E16gwQAB");
        assertPseudonymized(sanitized, "0055Y00000ExkfpQAB");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT+Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type+FROM+Account+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST",
    })
    void query_accounts(String query) {
        String jsonString = asJson("accounts_by_query.json");

        String endpoint = "https://test.salesforce.com/services/data/v51.0/query?q=" + query;

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "0055Y00000E16gwQAB");
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://test.salesforce.com/services/data/v51.0/query?q=SELECT+Alias,AccountId,ContactId,CreatedDate,CreatedById,Email,EmailEncodingKey,Id,IsActive,LastLoginDate,LastModifiedDate,ManagerId,Name,TimeZoneSidKey,Username,UserRoleId,UserType+FROM+User+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST", "users_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v51.0/query/SOME_TOKEN", "users_by_query.json"),

            InvocationExample.of("https://test.salesforce.com/services/data/v51.0/query?q=SELECT+Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type+FROM+Account+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST", "accounts_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v51.0/query/SOME_TOKEN", "accounts_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v51.0/composite/sobjects/Account?ids=0015Y00002c7g95QAA,0015Y00002c7g8uQAA&fields=Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type", "accounts.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v51.0/query?q=SELECT+Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type+FROM+Account+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST", "query_result_pagination.json")
            //InvocationExample.of("https://test.salesforce.com/services/data/v51.0/query?q=SELECT%20%28SELECT%20AccountId%2CActivityDate%2CActivityDateTime%2CActivitySubtype%2CActivityType%2CCallDurationInSeconds%2CCallType%2CCreatedDate%2CCreatedById%2CDurationInMinutes%2CEndDateTime%2CId%2CIsAllDayEvent%2CIsDeleted%2CIsHighPriority%2CIsTask%2CLastModifiedDate%2CLastModifiedById%2COwnerId%2CPriority%2CStartDateTime%2CStatus%2CWhatId%2CWhoId%20FROM%20ActivityHistories%20ORDER%20BY%20LastModifiedDate%20DESC%20NULLS%20LAST%29%20FROM%20Account%20where%20id%3D%270015Y00002c7g95QAA%27", "related_item_query.json"),
            //InvocationExample.of("https://test.salesforce.com/services/data/v51.0/query/SOME_TOKEN", "related_item_query.json")
        );
    }
}
