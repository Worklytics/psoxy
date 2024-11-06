package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public class EntraIDTests extends JavaRulesTestBaseCase {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.ENTRA_ID;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
            .sourceFamily("microsoft-365")
            .defaultScopeId("azure-ad")
            .sourceKind("entra-id")
            .build();

    // for cases where 'rulesTestSpec' will be overridden (Calendar)
    public static final String ENTRA_ID_API_EXAMPLES_PATH = "sources/microsoft-365/entra-id/example-api-responses/original/";

    @Test
    void user() {
        String jsonString = asJson(ENTRA_ID_API_EXAMPLES_PATH, "user.json");

        String endpoint = "https://graph.microsoft.com/v1.0/users/p~2343adsfasdfa";

        Collection<String> PII = Arrays.asList(
                "MeganB@M365x214355.onmicrosoft.com",
                "Megan",
                "Bowen",
                "Megan Bowen",
                "+1 412 555 0109"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "MeganB@M365x214355.onmicrosoft.com");
        assertPseudonymized(sanitized, "megan@M365x214355.onmicrosoft.com"); //her alias
        assertRedacted(sanitized,
                "Megan",
                "Bowen",
                "Megan Bowen",
                "+1 412 555 0109"
        );

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    void users() {
        String jsonString = asJson(ENTRA_ID_API_EXAMPLES_PATH, "users.json");

        String endpoint = "https://graph.microsoft.com/v1.0/users";

        Collection<String> PII = Arrays.asList(
                "john@worklytics.onmicrosoft.com",
                "Paul Allen",
                "no-mail-example@worklytics.onmicrosoft.com"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "john@worklytics.onmicrosoft.com");
        assertPseudonymized(sanitized, "no-mail-example@worklytics.onmicrosoft.com");
        assertRedacted(sanitized, "Paul Allen");
    }


    // case that ACTUALLY looks like what we call ...
    @ValueSource(strings = {
            "https://graph.microsoft.com/v1.0/users?$select=preferredLanguage,isResourceAccount,mail,city,displayName,givenName,jobTitle,employeeId,accountEnabled,otherMails,businessPhones,mobilePhone,officeLocation,surname,id,state,usageLocation,userType,department&$top=50",
            "https://graph.microsoft.com/v1.0/users?$select=preferredLanguage,isResourceAccount,mail,city,displayName,givenName,jobTitle,employeeId,accountEnabled,otherMails,businessPhones,mobilePhone,officeLocation,surname,id,state,usageLocation,userType,department&$top=50&$skiptoken=234234",
    })
    @ParameterizedTest
    void users_select(String endpoint) {
        String jsonString = asJson(ENTRA_ID_API_EXAMPLES_PATH, "users.json");

        Collection<String> PII = Arrays.asList(
                "john@worklytics.onmicrosoft.com",
                "Paul Allen"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "john@worklytics.onmicrosoft.com");
        assertRedacted(sanitized, "Paul Allen");
    }


    @Test
    void users_filter() {
        String endpoint = "https://graph.microsoft.com/v1.0/users?$filter=accountEnabled eq true";
        assertUrlBlocked(endpoint);
    }

    @Test
    void group() {
        String endpoint = "https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315";
        assertUrlWithQueryParamsAllowed(endpoint);
    }

    @Test
    void groups() {
        String endpoint = "https://graph.microsoft.com/v1.0/groups";
        assertUrlWithQueryParamsAllowed(endpoint);
    }

    @Test
    void groupMembers() {
        String endpoint = "https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315/members?$count=true";

        String jsonString = asJson(ENTRA_ID_API_EXAMPLES_PATH, "group-members.json");
        assertNotSanitized(jsonString,
                "Adele Vance",
                "AdeleV@M365x214355.onmicrosoft.com",
                "Vance",
                "+1 425 555 0109",
                "+1 502 555 0144",
                "Patti Fernandez",
                "PattiF@M365x214355.onmicrosoft.com"
        );

        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized,
                "Vance",
                "+1 425 555 0109",
                "+1 502 555 0144",
                "Patti Fernandez"
        );

        assertPseudonymized(sanitized,
                "AdeleV@M365x214355.onmicrosoft.com",
                "PattiF@M365x214355.onmicrosoft.com"
        );

    }


    public Stream<InvocationExample> getExamples() {
        return Stream.of(
                InvocationExample.of("https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315/members?$count=true", "group-members.json"),
                InvocationExample.of("https://graph.microsoft.com/v1.0/users/12398123012312", "user.json"),
                InvocationExample.of("https://graph.microsoft.com/v1.0/users", "users.json")
        );
    }
}