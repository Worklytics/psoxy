package co.worklytics.psoxy.rules.slack;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesBaseTestCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

public class SlackDiscoveryTests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.SLACK;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/slack";

    @Getter
    final String defaultScopeId = "slack";

    @SneakyThrows
    @Test
    void discovery_users_list() {
        String jsonString = asJson("discovery-users-list.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "john@domain.com",
            "felipe@domain.com",
            "bob@domain.com"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL("https://slack.com/api/discovery.users.list"), jsonString);

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "John Nobody", "felipe", "bob");
    }

    @SneakyThrows
    @Test
    void discovery_conversations_list() {
        String jsonString = asJson("discovery-conversations-list.json");

        String sanitized =
            sanitizer.sanitize(new URL("https://slack.com/api/discovery.conversations.list"), jsonString);

        // nothing to pseudonymize
        assertRedacted(sanitized, "This is the topic", "mpdm-primary-owner--first.person--second.person");
    }

    @SneakyThrows
    @Test
    void discovery_conversations_history() {
        String jsonString = asJson("discovery-conversations-history.json");

        String sanitized =
            sanitizer.sanitize(new URL("https://slack.com/api/discovery.conversations.history"), jsonString);

        Collection<String> PII = Arrays.asList(
            "W06CA4EAC","W0G81RDQT","W0N0ZQDED","W0R8EBMXP","W0G81RDQZ","W000000"
        );

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "Test message!", "<@U06CA4EAC|bjin>");
    }
}
