package co.worklytics.psoxy.rules.zoom;

import co.worklytics.psoxy.Rules;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static co.worklytics.psoxy.Rules.Rule;

/**
 * Prebuilt sanitization rules for Zoom API responses
 */
public class PrebuiltSanitizerRules {

    static final Rules ZOOM = Rules.builder()
        // List users
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/users/users
        .allowedEndpointRegex("^\\/v2\\/users(?:\\?.+)?")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/v2\\/users(?:\\?.+)?")
            .jsonPath("$.users[*].email")
            .build()
        )
        .pseudonymizationWithOriginal(Rule.builder()
            .relativeUrlRegex("\\/v2\\/users(?:\\?.+)?")
            // the original id is needed to iterate meetings by user
            .jsonPath("$.users[*].id")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/v2\\/users(?:\\?.+)?")
            // we don't care about names, profile pic, employee_unique_id (when SSO info)
            .jsonPath("$.users[*]['first_name','last_name','pic_url','employee_unique_id']")
            .build()
        )
        // Users' meetings
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/meetings
        .allowedEndpointRegex("^\\/v2\\/users\\/(?:[^\\/]*)\\/meetings(?:\\?.+)?")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/v2\\/users\\/(?:[^\\/]*)\\/meetings(?:\\?.+)?")
            .jsonPath("$.meetings[*].host_id")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/v2\\/users\\/(?:[^\\/]*)\\/meetings(?:\\?.+)?")
            .jsonPath("$.meetings[*].topic")
            .jsonPath("$.meetings[*].join_url")
            .build()
        )
        // Meeting details
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/meeting
        .allowedEndpointRegex("^\\/v2\\/meetings\\/(?:[^\\/]*)(?:\\?.+)?")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/v2\\/meetings\\/(?:[^\\/]*)(?:\\?.+)?")
            .jsonPath("$.host_id")
            .jsonPath("$.host_email")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/v2\\/meetings\\/(?:[^\\/]*)(?:\\?.+)?")
            .jsonPath("$.['topic','settings','agenda']")
            .jsonPath("$.['password','h323_password','pstn_password','encrypted_password']")
            .build()
        )
        // List past meeting instances
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetings
        .allowedEndpointRegex("^\\/v2\\/past_meetings\\/(?:.*)\\/instances")
        // Past meeting details
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetingdetails
        .allowedEndpointRegex("^\\/v2\\/past_meetings\\/(?:[^\\/]*)")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/v2\\/past_meetings\\/(?:[^\\/]*)")
            .jsonPath("$.host_id")
            .jsonPath("$.user_email")
            .jsonPath("$.host_email")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/v2\\/past_meetings\\/(?:[^\\/]*)")
            // we don't care about user's name
            .jsonPath("$.['user_name','topic']")
            .build()
        )
        // Past meeting participants
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetingparticipants
        .allowedEndpointRegex("^\\/v2\\/past_meetings\\/(?:.*)\\/participants(?:\\?.+)?")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/v2\\/past_meetings\\/(?:.*)\\/participants(?:\\?.+)?")
            .jsonPath("$.participants[*].id")
            .jsonPath("$.participants[*].user_email")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/v2\\/past_meetings\\/(?:.*)\\/participants(?:\\?.+)?")
            // we don't care about user's name
            .jsonPath("$.participants[*].name")
            .build()
        )
        .build();

    static public final Map<String, Rules> ZOOM_PREBUILT_RULES_MAP = ImmutableMap.<String, Rules>builder()
        .put("zoom", ZOOM)
        .build();
}
