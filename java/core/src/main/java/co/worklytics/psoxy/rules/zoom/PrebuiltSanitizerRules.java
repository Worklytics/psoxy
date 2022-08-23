package co.worklytics.psoxy.rules.zoom;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.Transform;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Zoom API responses
 */
public class PrebuiltSanitizerRules {

    static final Rules2 MEETINGS_ENDPOINTS = Rules2.builder()
        // Users' meetings
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/meetings
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/v2/users/(?:[^/]*)/meetings(?:\\?.+)?")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.meetings[*].['host_id','host_email']")
                .build())
            .transform(Transform.Redact.builder()
                .jsonPath("$.meetings[*]['topic','join_url']")
                .build())
            .build()
        )
        // Meeting details
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/meeting
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/v2/meetings/(?:[^/]*)(?:\\?.+)?")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.['host_id','host_email']")
                .build())
            .transform(Transform.Redact.builder()
                .jsonPath("$.['topic','settings','agenda','custom_keys']")
                .jsonPath("$.['password','h323_password','pstn_password','encrypted_password']")
                .build())
            .build()
        )
        // List past meeting instances
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetings
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/v2/past_meetings/(?:.*)/instances")
            .build())
        // Past meeting details
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetingdetails
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/v2/past_meetings/(?:[^\\/]*)")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.['host_id','user_email','host_email']")
                .build()
            )
            .transform(Transform.Redact.builder()
                // we don't care about user's name
                .jsonPath("$.['user_name','topic']")
                .build())
            .build())
        // Past meeting participants
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetingparticipants
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/v2/past_meetings/(?:.*)/participants(?:\\?.+)?")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.participants[*].['id','user_email']")
                .build()
            )
            .transform(Transform.Redact.builder()
                // we don't care about user's name
                .jsonPath("$.participants[*].['name','registrant_id']")
                .build())
            .build())
        .build();


    static final Rules2 REPORT_ENDPOINTS = Rules2.builder()
        // List meetings
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/methods#operation/reportMeetings
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/v2/report/users/(?:.*)/meetings(?:\\?.+)?")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.meetings[*]['host_id','user_email','host_email']")
                .build()
            )
            .transform(Transform.Redact.builder()
                // we don't care about user's name
                .jsonPath("$.meetings[*]['user_name','topic','custom_keys','tracking_fields']")
                .build())
            .build())
        // Past meeting details
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/methods#operation/reportMeetingDetails
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/v2/report/meetings/(?:[^/]*)")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.['host_id','user_email','host_email']")
                .build()
            )
            .transform(Transform.Redact.builder()
    // we don't care about user's name
                .jsonPath("$.['user_name','topic','custom_keys','tracking_fields']")
                .build())
        .build())
        // Past meeting participants
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/methods#operation/reportMeetingParticipants
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/v2/report/meetings/(?:.*)/participants(?:\\?.+)?")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.participants[*].id")
                .jsonPath("$.participants[*].user_email")
                .build()
            )
            .transform(Transform.Redact.builder()
    // we don't care about user's name
                .jsonPath("$.participants[*].['name','registrant_id']")
                .build())
        .build())
        .build();




    static final Rules2 USERS_ENDPOINTS = Rules2.builder()
        // List users
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/users/users
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("/v2/users(?:\\?.+)?")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.users[*].email")
                .build()
            )
            .transform(Transform.Pseudonymize.builder()
                .includeOriginal(true)// the original id is needed to iterate meetings by user
                .jsonPath("$.users[*].id")
                .build()
            )
            .transform(Transform.Redact.builder()
                // we don't care about names, profile pic, employee_unique_id (when SSO info)
                .jsonPath("$.users[*]['first_name','last_name','pic_url','employee_unique_id']")
                .build()
            )
            .build())
        .build();

    static final Rules2 ZOOM = USERS_ENDPOINTS
        .withAdditionalEndpoints(MEETINGS_ENDPOINTS.getEndpoints())
        .withAdditionalEndpoints(REPORT_ENDPOINTS.getEndpoints());


    static public final Map<String, RuleSet> ZOOM_PREBUILT_RULES_MAP = ImmutableMap.<String, RuleSet>builder()
        .put("zoom", ZOOM)
        .build();
}
