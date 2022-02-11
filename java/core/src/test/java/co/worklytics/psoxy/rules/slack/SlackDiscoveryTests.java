package co.worklytics.psoxy.rules.slack;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesBaseTestCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SlackDiscoveryTests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.SLACK;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/slack";

    @Getter
    final String defaultScopeId = "slack";

    @Getter
    final String yamlSerializationFilepath = "slack/discovery";

    @SneakyThrows
    @ValueSource(strings = {
        "https://slack.com/api/discovery.enterprise.info",
        "https://slack.com/api/discovery.conversations.list#fragment", // fragments get discarded
        "https://slack.com/api/discovery.conversations.list",
        "https://slack.com/api/discovery.conversations.list?team=X&offset=Y&only_im=true",
        "https://slack.com/api/discovery.conversations.history",
        "https://slack.com/api/discovery.conversations.history?channel=X&limit=10",
        "https://slack.com/api/discovery.users.list",
        "https://slack.com/api/discovery.users.list?limit=20&include_deleted=true",
    })
    @ParameterizedTest
    void allowedEndpointRegex_allowed(String url) {
        assertTrue(sanitizer.isAllowed(new URL(url)), url + " should be allowed");
    }

    @SneakyThrows
    @ValueSource(strings = {
        // variations on allowed
        "https://slack.com/api/discovery.conversations.list/",
        "https://slack.com/api/discovery_conversations-list",
        "https://slack.com/api/discovery-conversations-history",
        "https://slack.com/api/discovery users list",
        // all the rest of the discovery methods
        "https://slack.com/api/discovery.user.info",
        "https://slack.com/api/discovery.user.conversations",
        "https://slack.com/api/discovery.conversations.recent",
        "https://slack.com/api/discovery.conversations.edits",
        "https://slack.com/api/discovery.conversations.info",
        "https://slack.com/api/discovery.conversations.members",
        "https://slack.com/api/discovery.conversations.renames",
        "https://slack.com/api/discovery.conversations.reactions",
        "https://slack.com/api/discovery.conversations.search",
        "https://slack.com/api/discovery.chat.info",
        "https://slack.com/api/discovery.chat.update",
        "https://slack.com/api/discovery.chat.delete",
        "https://slack.com/api/discovery.chat.tombstone",
        "https://slack.com/api/discovery.chat.restore",
        "https://slack.com/api/discovery.drafts.list",
        "https://slack.com/api/discovery.draft.info",
        "https://slack.com/api/discovery.files.list",
        "https://slack.com/api/discovery.file.info",
        "https://slack.com/api/discovery.file.tombstone",
        "https://slack.com/api/discovery.file.restore",
        "https://slack.com/api/discovery.file.delete",
        "https://slack.com/api/discovery.files.release",
    })
    @ParameterizedTest
    void allowedEndpointRegex_blocked(String url) {
        assertFalse(sanitizer.isAllowed(new URL(url)), url + " should be blocked");
    }

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
        assertRedacted(sanitized, "Test message!",
            "<@U06CA4EAC|bjin>",
            "text with rich block",
            "This is likely a pun about the weather.",
            "We're withholding a pun from you",
            "Leg end nary a laugh, Ink.");

    }
}
