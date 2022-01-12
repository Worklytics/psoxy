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
        .allowedEndpointRegex("^\\/users(?:\\?.+)?")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/users(?:\\?.+)?")
            .jsonPath("$.users[*].id")
            .jsonPath("$.users[*].email")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/users(?:\\?.+)?")
            // we don't care about names, profile pic, employee_unique_id (when SSO info)
            .jsonPath("$.users[*]['first_name','last_name','pic_url','employee_unique_id']")
            .build()
        )
        // Users' meetings
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/meetings
        .allowedEndpointRegex("^\\/users\\/(?:[^\\/]*)\\/meetings(?:\\?.+)?")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/users\\/(?:[^\\/]*)\\/meetings(?:\\?.+)?")
            .jsonPath("$.meetings[*].host_id")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/users\\/(?:[^\\/]*)\\/meetings(?:\\?.+)?")
            .jsonPath("$.meetings[*].topic")
            .build()
        )
        // Meeting details
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/meeting
        .allowedEndpointRegex("^\\/meetings\\/(?:[^\\/]*)(?:\\?.+)?")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/meetings\\/(?:[^\\/]*)(?:\\?.+)?")
            .jsonPath("$.host_id")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/meetings\\/(?:[^\\/]*)(?:\\?.+)?")
            .jsonPath("$.['topic','settings','agenda']")
            .build()
        )
        // List past meeting instances
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetings
        .allowedEndpointRegex("^\\/past_meetings\\/(?:.*)\\/instances")
        // Past meeting details
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetingdetails
        .allowedEndpointRegex("^\\/past_meetings\\/(?:[^\\/]*)")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/past_meetings\\/(?:[^\\/]*)")
            .jsonPath("$.host_id")
            .jsonPath("$.user_email")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/past_meetings\\/(?:[^\\/]*)")
            // we don't care about user's name
            .jsonPath("$.user_name")
            .build()
        )
        // Past meeting participants
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetingparticipants
        .allowedEndpointRegex("^\\/past_meetings\\/(?:.*)\\/participants(?:\\?.+)")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/past_meetings\\/(?:.*)\\/participants(?:\\?.+)")
            .jsonPath("$.participants[*].id")
            .jsonPath("$.participants[*].user_email")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/past_meetings\\/(?:.*)\\/participants(?:\\?.+)")
            // we don't care about user's name
            .jsonPath("$.participants[*].name")
            .build()
        )
        .build();

    static public final Map<String, Rules> ZOOM_PREBUILT_RULES_MAP = ImmutableMap.<String, Rules>builder()
        .put("zoom", ZOOM)
        .build();
}
