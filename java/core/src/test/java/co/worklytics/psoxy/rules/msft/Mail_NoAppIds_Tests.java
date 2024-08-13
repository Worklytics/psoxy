
package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

//TODO: fix this re-use via inheritance; makes tests brittle; we should inject this rule set into
// the directory tests, or something like that
public class Mail_NoAppIds_Tests extends EntraIDTests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_MAIL_NO_APP_IDS;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("microsoft-365")
        .defaultScopeId("azure-ad")
        .sourceKind("outlook-mail")
        .rulesFile("outlook-mail_no-app-ids")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized_no-app-ids/")
        .build();

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    public void messages(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/mailFolders/SentItems/messages";
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
    @ValueSource(strings = {"v1.0"})
    public void message(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
                "/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/messages/AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAEJAAAiIsqMbYjsT5e-T7KzowPTAAQSfAIWAAA=";
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
    @ValueSource(strings = {"v1.0"})
    public void mailboxSettings(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
                "/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/mailboxSettings";

        String jsonResponse = asJson("MailboxSettings_" + apiVersion + ".json");

        assertNotSanitized(jsonResponse,
                "should be redacted 1",
                "should be redacted 2"
        );

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
                "should be redacted 1",
                "should be redacted 2"
        );

        assertUrlAllowed(endpoint);
        assertUrlWithQueryParamsBlocked(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    public void mailboxPaging(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
                "/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/mailFolders('SentItems')/messages?$filter=SentDateTime+gt+2019-12-30T00%3a00%3a00Z+and+SentDateTime+lt+2022-05-16T00%3a00%3a00Z&%24top=10&$skip=10";
        assertUrlAllowed(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    public void inboxBlocked(String apiVersion) {
        String pagingEndpoint = "https://graph.microsoft.com/" + apiVersion +
                "/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/mailFolders('Inbox')/messages?$filter=SentDateTime+gt+2019-12-30T00%3a00%3a00Z+and+SentDateTime+lt+2022-05-16T00%3a00%3a00Z&%24top=10&$skip=10";
        assertUrlBlocked(pagingEndpoint);

        String endpoint = "https://graph.microsoft.com/" + apiVersion +
                "/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/mailFolders/Inbox/messages?$filter=SentDateTime+gt+2019-12-30T00%3a00%3a00Z+and+SentDateTime+lt+2022-05-16T00%3a00%3a00Z&%24top=10&$skip=10";
        assertUrlBlocked(endpoint);
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
                InvocationExample.of("https://graph.microsoft.com/v1.0/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/mailboxSettings", "MailboxSettings_v1.0.json"),
                InvocationExample.of("https://graph.microsoft.com/v1.0/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/messages/AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAEJAAAiIsqMbYjsT5e-T7KzowPTAAQSfAIWAAA=", "Message_v1.0.json"),
                InvocationExample.of("https://graph.microsoft.com/v1.0/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug/mailFolders('SentItems')/messages?$filter=SentDateTime+gt+2019-12-30T00%3a00%3a00Z+and+SentDateTime+lt+2022-05-16T00%3a00%3a00Z&%24top=10&$skip=10", "Messages_SentItems_v1.0.json")
        );
    }
}