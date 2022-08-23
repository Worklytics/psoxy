package co.worklytics.psoxy.rules.slack;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RuleSet;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class SlackDiscoveryTests extends JavaRulesTestBaseCase {

    @Getter
    final RuleSet rulesUnderTest = PrebuiltSanitizerRules.SLACK;

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
            "https://slack.com/api/discovery.conversations.recent?team=X&limit=10&latest=123",
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
            "https://slack.com/api/discovery.conversation.info/",
            // all the rest of the discovery methods
            "https://slack.com/api/discovery.user.info",
            "https://slack.com/api/discovery.user.conversations",
            "https://slack.com/api/discovery.conversations.edits",
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

        assertThat(sanitized, hasJsonPath("$.users[*].profile[*].team"));
        assertThat(sanitized, hasJsonPath("$.users[*].profile[*].email"));
        assertThat(sanitized, hasJsonPath("$.users[0].profile.keys()", hasSize(2)));
        assertThat(sanitized, hasNoJsonPath("$.users[0].profile.keys()"), not(hasProperty("display_name")));

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

        Collection<String> PIItoPseudonymize = Arrays.asList(
                "W06CA4EAC", "W0G81RDQT", "W0N0ZQDED", "W0R8EBMXP", "W0G81RDQZ", "W000000", "U02DU306H0B",
                "REPLYUSER",
                "some parent user"
        );
        Collection<String> dataToRedact = Arrays.asList(
                "Test message!",
                "<@U06CA4EAC|bjin>",
                "text with rich block",
                "Some new text",
                "check this out!",
                "Jose (ENT)",
                "Jose",
                "This is likely a pun about the weather.",
                "We're withholding a pun from you",
                "Leg end nary a laugh, Ink.",
                "Some other text",
                "https://badpuns.example.com/puns/123.png",
                "permalink value"
        );

        assertNotSanitized(jsonString, PIItoPseudonymize);
        assertNotSanitized(jsonString, dataToRedact);

        String sanitized =
                sanitizer.sanitize(new URL("https://slack.com/api/discovery.conversations.history"), jsonString);

        assertPseudonymized(sanitized, PIItoPseudonymize);
        assertRedacted(sanitized, dataToRedact);
    }

    @SneakyThrows
    @Test
    void discovery_conversations_recent() {
        String jsonString = asJson("discovery-conversations-recent.json");

        String sanitized =
                sanitizer.sanitize(new URL("https://slack.com/api/discovery.conversations.recent"), jsonString);

        // nothing to redact / pseudonymize
        assertJsonEquals(jsonString, sanitized);
    }

    @SneakyThrows
    @Test
    void discovery_conversations_info() {
        String jsonString = asJson("discovery-conversations-info.json");

        String sanitized =
                sanitizer.sanitize(new URL("https://slack.com/api/discovery.conversations.info"), jsonString);

        Collection<String> PII = Arrays.asList(
                "W0N9HDWUR"
        );

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "Collaboration about Project X",
                "Launch date scheduled for 07/01",
                "project-x",
                "project X",
                "project-y");

    }

    @SneakyThrows
    @Test
    void discovery_enterprise_info() {
        String jsonString = asJson("discovery-enterprise-info.json");

        String sanitized =
                sanitizer.sanitize(new URL("https://slack.com/api/discovery.enterprise.info"), jsonString);

        assertRedacted(sanitized, "icon", "image",
                "DevGrid - W1",
                "1st Workspace under Test Grid",
                "DevGrid - W2",
                "2nd Workspace under Test Grid",
                "Test Grid");

    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://slack.com/api/discovery.enterprise.info", "discovery-enterprise-info.json"),
            InvocationExample.of("https://slack.com/api/discovery.conversations.info", "discovery-conversations-info.json"),
            InvocationExample.of("https://slack.com/api/discovery.conversations.recent", "discovery-conversations-recent.json"),
            InvocationExample.of("https://slack.com/api/discovery.conversations.history", "discovery-conversations-history.json"),
            InvocationExample.of("https://slack.com/api/discovery.users.list", "discovery-users-list.json"),
            InvocationExample.of("https://slack.com/api/discovery.conversations.list", "discovery-conversations-list.json")
        );
    }
}
