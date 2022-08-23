package co.worklytics.psoxy.rules.slack;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.Transform;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
/**
 * Prebuilt sanitization rules for Slack Discovery API responses
 */
public class PrebuiltSanitizerRules {

    static final RuleSet SLACK = Rules2.builder()
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/api/discovery\\.enterprise\\.info(?:\\?.+)?")
            .transform(Transform.Redact.builder()
                // we don't care about names
                .jsonPath("$.enterprise.teams[*]['name','description','icon','enterprise_name']")
                .jsonPath("$.enterprise['icon','name']")
                .build())
            .build())
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/api/discovery\\.users\\.list(?:\\?.+)?")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.users[*].id")
                .jsonPath("$.users[*].profile.email")
                .build())
            .transform(Transform.Redact.builder()
                // we don't care about names
                .jsonPath("$.users[*]['name','real_name']")
                // profile contains a lot of stuff. We just mainly need "email" and "team". clean it up
                // TODO: ideally this is a whitelist
                .jsonPath("$.users[*].profile['title','phone','skype','first_name','last_name','real_name','real_name_normalized','display_name','display_name_normalized']")
                .jsonPath("$.users[*].profile['fields','pronouns','status_text','status_emoji','status_emoji_display_info','status_expiration','avatar_hash']")
                .jsonPath("$.users[*].profile['image_original','is_custom_image','image_24','image_32','image_48','image_72','image_192','image_512','image_1024','status_text_canonical']")
                .build())
            .build())
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/api/discovery\\.conversations\\.(list|recent)(?:\\?.+)?")
            // no PII
            // redact channel name, topic and purpose
            .transform(Transform.Redact.builder()
                // we don't care about names
                // topic and purpose contains user ids, not used at all, so just get rid of the entire content
                .jsonPath("$.channels[*]['name','topic','purpose']")
                .build())
            .build())
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/api/discovery\\.conversations\\.info(?:\\?.+)?")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.info[*].creator")
                .build())
            .transform(Transform.Redact.builder()
                // we don't care about names
                // topic and purpose contains user ids, not used at all, so just get rid of the entire content
                .jsonPath("$.info[*]['name','name_normalized','previous_names','topic','purpose']")
                .build())
            .build())
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/api/discovery\\.conversations\\.history(?:\\?.+)?")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.messages[*].user")
                .jsonPath("$.messages[*].files[*].user")
                .jsonPath("$.messages[*].reactions[*].users[*]")
                .jsonPath("$.messages[*].replies[*].user")
                .jsonPath("$.messages[*].replies[*].parent_user_id")
                .jsonPath("$.messages[*].reply_users[*]")
                .jsonPath("$.messages[*].edited.user")
                .jsonPath("$.messages[*].blocks[*].elements[*].elements[*].user_id") // mentions in rich blocks
                .build())
            .transform(Transform.Redact.builder()
                // we don't care about text
                // username is a variation of user, so just skip it to avoid references
                .jsonPath("$.messages[*]['text','username','permalink']")
                .jsonPath("$.messages[*]..['text']")
                .jsonPath("$.messages[*].user_profile")
                // Thumbnails name or url may reveal content
                .jsonPath("$.messages[*].attachments[*]['fallback','service_name', 'thumb_url','thumb_width','thumb_height']")
                .jsonPath("$.messages[*].files[*]['thumb_url','thumb_width','thumb_height','thumb_tiny']")
                .build())
            .build())
        .build();

    static public final Map<String, RuleSet> SLACK_DEFAULT_RULES_MAP = ImmutableMap.<String, RuleSet>builder()
        .put("slack", SLACK)
        .build();
}
