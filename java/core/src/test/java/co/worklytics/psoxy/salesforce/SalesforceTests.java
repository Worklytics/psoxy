package co.worklytics.psoxy.salesforce;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.salesforce.PrebuiltSanitizerRules;
import lombok.Getter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

public class SalesforceTests extends JavaRulesTestBaseCase {

    @Getter
    final RuleSet rulesUnderTest = PrebuiltSanitizerRules.SALESFORCE;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/salesforce";

    @Getter
    final String defaultScopeId = "salesforce";

    @Getter
    final String yamlSerializationFilepath = "salesforce/salesforce";

    @Disabled // not reliable; seems to have different value via IntelliJ/AWS deployment and my
    // laptop's maven, which doesn't make any sense, given that binary deployed to AWS was built via
    // maven on my laptop - so something about Surefire/Junit runner used by maven??
    @Test
    void sha() {
        this.assertSha("7869e465607b7a00b4bd75a832a9ed1f811ce7f2");
    }

    @Test
    void account_describe() {
        String jsonString = asJson(exampleDirectoryPath, "account_describe.json");

        String endpoint = "https://test.salesforce.com/services/data/v51.0/sobjects/Account/describe";

        assertUrlWithQueryParamsBlocked(endpoint);
        assertUrlWithSubResourcesBlocked(endpoint);

        String sanitized = this.sanitize(endpoint, jsonString);
    }

    @Test
    void activityHistory_describe() {
        String jsonString = asJson(exampleDirectoryPath, "activityHistory_describe.json");

        String endpoint = "https://test.salesforce.com/services/data/v51.0/sobjects/ActivityHistory/describe";

        assertUrlWithQueryParamsBlocked(endpoint);
        assertUrlWithSubResourcesBlocked(endpoint);

        String sanitized = this.sanitize(endpoint, jsonString);
    }

    @Test
    void accounts_by_id() {
        String jsonString = asJson(exampleDirectoryPath, "accounts.json");

        String endpoint = "https://test.salesforce.com/services/data/v51.0/composite/sobjects/Account?ids=0015Y00002c7g95QAA,0015Y00002c7g8uQAA&fields=Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type";

        String sanitized = this.sanitize(endpoint, jsonString);
    }

    @Test
    void query_by_id() {
        String jsonString = asJson(exampleDirectoryPath, "basic_query.json");

        String endpoint = "https://test.salesforce.com/services/data/v51.0/query?q=SELECT%20Id%20from%20Account%20ORDER%20BY%20Id%20ASC";

        String sanitized = this.sanitize(endpoint, jsonString);
    }

    @Test
    void query_with_related() {
        String jsonString = asJson(exampleDirectoryPath, "related_item_query.json");

        String endpoint = "https://test.salesforce.com/services/data/v51.0/query?q=SELECT%20%28SELECT%20AccountId%2CActivityDate%2CActivityDateTime%2CActivitySubtype%2CActivityType%2CCallDurationInSeconds%2CCallType%2CCreatedDate%2CCreatedById%2CDurationInMinutes%2CEndDateTime%2CId%2CIsAllDayEvent%2CIsDeleted%2CIsHighPriority%2CIsTask%2CLastModifiedDate%2CLastModifiedById%2COwnerId%2CPriority%2CStartDateTime%2CStatus%2CWhatId%2CWhoId%20FROM%20ActivityHistories%20ORDER%20BY%20LastModifiedDate%20DESC%20NULLS%20LAST%29%20FROM%20Account%20where%20id%3D%270015Y00002c7g95QAA%27";

        String sanitized = this.sanitize(endpoint, jsonString);
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return new ArrayList<InvocationExample>().stream();
    }
}