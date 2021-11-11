package co.worklytics.psoxy.rules.slack;

import co.worklytics.psoxy.Rules;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static co.worklytics.psoxy.Rules.Rule;

/**
 * Prebuilt sanitization rules for Slack Discovery API responses
 */
public class PrebuiltSanitizerRules {

    static final Rules SLACK = Rules.builder()
        .allowedEndpointRegex("^/api/discovery.conversations.list.*")
        .allowedEndpointRegex("^/api/discovery.conversations.history.*")
        .allowedEndpointRegex("^/api/discovery.users.list.*")
        // users
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("/api/discovery.users.list.*")
            .jsonPath("$.users[*].id")
            .jsonPath("$.users[*].profile.email")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("/api/discovery.users.list.*")
            // we don't care about names
            .jsonPath("$.users[*]['name','real_name']")
            .jsonPath("$.users[*].profile['name','real_name','display_name','display_name_normalized','real_name_normalized','title','phone','skype','first_name','last_name']")
            .build()
        )
        // conversations list
        // no PII
        // redact channel name, topic and purpose
        .redaction(Rule.builder()
            .relativeUrlRegex("/api/discovery.conversations.list.*")
            // we don't care about names
            // topic and purpose contains user ids, not used at all, so just get rid of the entire content
            .jsonPath("$.channels[*]['name','topic','purpose']")
            .build()
        )
        // conversations history
        // no PII
        // redact channel name, topic and purpose
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("/api/discovery.conversations.history.*")
            .jsonPath("$.messages[*].user")
            .jsonPath("$.messages[*].reactions[*].users[*]")
            .jsonPath("$.messages[*].edited.user")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("/api/discovery.conversations.history.*")
            // we don't care about text
            // username is a variation of user, so just skip it to avoid references
            .jsonPath("$.messages[*]['text','username']")
            .build()
        )
        .build();

    static public final Map<String, Rules> SLACK_PREBUILT_RULES_MAP = ImmutableMap.<String, Rules>builder()
        .put("slack", SLACK)
        .build();
}
