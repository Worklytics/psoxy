package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import lombok.Getter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

public class MailTests extends JavaRulesTestBaseCase {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_MAIL;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("microsoft-365")
        .defaultScopeId("azure-ad")
        .sourceKind("outlook-mail")
        .build();

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
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
    @ValueSource(strings = {"v1.0"})
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
    @ValueSource(strings = {"v1.0"})
    public void mailboxSettings(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/mailboxSettings";

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
            "/users/4ea7fc01-0264-4e84-b85e-9e49fba4de97/mailFolders('SentItems')/messages?$filter=SentDateTime+gt+2019-12-30T00%3a00%3a00Z+and+SentDateTime+lt+2022-05-16T00%3a00%3a00Z&%24top=10&$skip=10";
        assertUrlAllowed(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    public void inboxBlocked(String apiVersion) {
        String pagingEndpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/4ea7fc01-0264-4e84-b85e-9e49fba4de97/mailFolders('Inbox')/messages?$filter=SentDateTime+gt+2019-12-30T00%3a00%3a00Z+and+SentDateTime+lt+2022-05-16T00%3a00%3a00Z&%24top=10&$skip=10";
        assertUrlBlocked(pagingEndpoint);

        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/4ea7fc01-0264-4e84-b85e-9e49fba4de97/mailFolders/Inbox/messages?$filter=SentDateTime+gt+2019-12-30T00%3a00%3a00Z+and+SentDateTime+lt+2022-05-16T00%3a00%3a00Z&%24top=10&$skip=10";
        assertUrlBlocked(endpoint);
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/mailboxSettings", "MailboxSettings_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/messages/AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAEJAAAiIsqMbYjsT5e-T7KzowPTAAQSfAIWAAA=", "Message_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/4ea7fc01-0264-4e84-b85e-9e49fba4de97/mailFolders('SentItems')/messages?$filter=SentDateTime+gt+2019-12-30T00%3a00%3a00Z+and+SentDateTime+lt+2022-05-16T00%3a00%3a00Z&%24top=10&$skip=10", "Messages_SentItems_v1.0.json")
        );
    }
  }