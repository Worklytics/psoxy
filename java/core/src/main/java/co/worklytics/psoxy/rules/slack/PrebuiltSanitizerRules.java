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
        .allowedEndpointRegex("^\\/api\\/discovery\\.enterprise\\.info(?:\\?.+)?")
        .allowedEndpointRegex("^\\/api\\/discovery\\.conversations\\.list(?:\\?.+)?")
        .allowedEndpointRegex("^\\/api\\/discovery\\.conversations\\.history(?:\\?.+)?")
        .allowedEndpointRegex("^\\/api\\/discovery\\.users\\.list(?:\\?.+)?")
        // users
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/api\\/discovery\\.users\\.list(?:\\?.+)?")
            .jsonPath("$.users[*].id")
            .jsonPath("$.users[*].profile.email")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/api\\/discovery\\.users\\.list(?:\\?.+)?")
            // we don't care about names
            .jsonPath("$.users[*]['name','real_name']")
            // profile contains a lot of stuff. We just mainly need "email" and "team". clean it up
            // TODO: ideally this is a whitelist
            .jsonPath("$.users[*].profile['title','phone','skype','first_name','last_name','real_name','real_name_normalized','display_name','display_name_normalized']")
            .jsonPath("$.users[*].profile['fields','pronouns','status_text','status_emoji','status_emoji_display_info','status_expiration','avatar_hash']")
            .jsonPath("$.users[*].profile['image_original','is_custom_image','image_24','image_32','image_48','image_72','image_192','image_512','image_1024','status_text_canonical']")
            .build()
        )
        // conversations list
        // no PII
        // redact channel name, topic and purpose
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/api\\/discovery\\.conversations\\.list(?:\\?.+)?")
            // we don't care about names
            // topic and purpose contains user ids, not used at all, so just get rid of the entire content
            .jsonPath("$.channels[*]['name','topic','purpose']")
            .build()
        )
        // conversations history
        // no PII
        // redact channel name, topic and purpose
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/api\\/discovery\\.conversations\\.history(?:\\?.+)?")
            .jsonPath("$.messages[*].user")
            .jsonPath("$.messages[*].reactions[*].users[*]")
            .jsonPath("$.messages[*].edited.user")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/api\\/discovery\\.conversations\\.history(?:\\?.+)?")
            // we don't care about text
            // username is a variation of user, so just skip it to avoid references
            .jsonPath("$.messages[*]['text','username']")
            .jsonPath("$.messages[*]..['text']")
            .jsonPath("$.messages[*].attachments[*]['fallback','service_name']")
            .build()
        )
        .build();

    static public final Map<String, Rules> SLACK_DEFAULT_RULES_MAP = ImmutableMap.<String, Rules>builder()
        .put("slack", SLACK)
        .build();
}
