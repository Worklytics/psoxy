package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import lombok.Getter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class MailTests extends JavaRulesTestBaseCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_MAIL;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/microsoft-365/outlook-mail";

    @Getter
    final String defaultScopeId = "azure-ad";

    @Getter
    final String yamlSerializationFilepath = "microsoft-365/outlook-mail";


    @ParameterizedTest
    @ValueSource(strings = {"v1.0", "beta"})
    public void messages(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/mailFolders/SentItems/messages";
        String jsonResponse = asJson("Messages_SentItems_"+ apiVersion + ".json");
        assertNotSanitized(jsonResponse,
            "MeganB@M365x214355.onmicrosoft.com",
            "Megan Bowen",
            "AlexW@M365x214355.onmicrosoft.com",
            "Alex Wilber",
            "Megan Bowen shared \\\"Pricing Guidelines for XT1000\\\" with you.", //subject
            "Here's the document that Megan Bowen shared with you." //body
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertPseudonymized(sanitized,
            "MeganB@M365x214355.onmicrosoft.com",
            "AlexW@M365x214355.onmicrosoft.com"
        );
        assertRedacted(sanitized,
            "Megan Bowen",
            "Alex Wilber",
            "Megan Bowen shared \\\"Pricing Guidelines for XT1000\\\" with you.", //subject
            "Here's the document that Megan Bowen shared with you." //body
        );
        assertUrlWithQueryParamsAllowed(endpoint); //paging
    }


    @ParameterizedTest
    @ValueSource(strings = {"v1.0", "beta"})
    public void message(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/messages/AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAEJAAAiIsqMbYjsT5e-T7KzowPTAAQSfAIWAAA=";
        String jsonResponse = asJson("Message_" + apiVersion + ".json");

        assertNotSanitized(jsonResponse,
            "MeganB@M365x214355.onmicrosoft.com",
            "Megan Bowen",
            "AlexW@M365x214355.onmicrosoft.com",
            "Alex Wilber",
            "Megan Bowen shared \\\"Pricing Guidelines for XT1000\\\" with you.", //subject
            //NOTE: in practice, don't expect bodies in API responses bc using MetaData only
            "Here's the document that Megan Bowen shared with you." //body
        );

        String sanitized = sanitize(endpoint, jsonResponse);

        assertPseudonymized(sanitized,
            "MeganB@M365x214355.onmicrosoft.com",
            "AlexW@M365x214355.onmicrosoft.com"
        );
        assertRedacted(sanitized,
            "Megan Bowen",
            "Alex Wilber",
            "Megan Bowen shared \\\"Pricing Guidelines for XT1000\\\" with you.", //subject
            "Here's the document that Megan Bowen shared with you." //body
            );
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0", "beta"})
    public void mailboxSettings(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/mailboxSettings";

        assertUrlAllowed(endpoint);
        assertUrlWithQueryParamsBlocked(endpoint);
    }
}

