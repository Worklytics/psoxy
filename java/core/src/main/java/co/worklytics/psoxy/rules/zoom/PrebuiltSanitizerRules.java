package co.worklytics.psoxy.rules.zoom;

import com.avaulta.gateway.rules.Endpoint;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.transforms.Transform;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Prebuilt sanitization rules for Zoom API responses
 */
public class PrebuiltSanitizerRules {

    static final Rules2 MEETINGS_ENDPOINTS = Rules2.builder()
        // Users' meetings
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/meetings
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/users/{userId}/meetings")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.meetings[*]['host_id','host_email']")
                .build())
            .transform(Transform.Redact.builder()
                .jsonPath("$.meetings[*]['topic','join_url','start_url','agenda']")
                .build())
            .build()
        )
        // Meeting details
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/meeting
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/meetings/{meetingId}")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.['host_id','host_email']")
                .jsonPath("$..email")
                .jsonPath("$..contact_email")
                .jsonPath("$..participants[*]")
                .build())
            .transform(Transform.Redact.builder()
                .jsonPath("$.['topic','agenda','custom_keys']")
                .jsonPath("$.settings.['alternative_hosts','enforce_login_domains','approved_or_denied_countries_or_regions','audio_conference_info','name','authentication_name','authentication_option','contact_name','custom_keys','global_dial_in_countries','global_dial_in_numbers']")
                .jsonPath("$.['password','h323_password','pstn_password','encrypted_password','start_url']")
                .jsonPath("$..join_url")
                .jsonPath("$..name")
                .build())
            .build()
        )
        // Meeting summary
        // https://developers.zoom.us/docs/api/meetings/#tag/meetings/GET/meetings/{meetingId}/meeting_summary
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/meetings/{meetingId}/meeting_summary")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$['meeting_host_id','meeting_host_email', 'summary_last_modified_user_id','summary_last_modified_user_email']")
                .build())
            .transform(Transform.TextDigest.builder()
                .jsonPath("$..summary_overview")
                .jsonPath("$.summary_details[*].label")
                .jsonPath("$.summary_details[*].summary")
                .jsonPath("$.edited_summary.summary_details")
                .jsonPath("$..next_steps[*]")
                .build())
            .transform(Transform.Redact.builder()
                .jsonPath("$['meeting_topic','summary_title']")
                .build())
            .build()
        )
        // List past meeting instances
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetings
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/past_meetings/{meetingId}/instances")
            .build())
        // Past meeting details
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetingdetails
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/past_meetings/{meetingId}")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.['host_id','user_email','host_email']")
                .build()
            )
            .transform(Transform.Redact.builder()
                // we don't care about user's name
                .jsonPath("$.['user_name','topic','agenda']")
                .build())
            .build())
        // Past meeting participants
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/meetings/pastmeetingparticipants
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/past_meetings/{meetingId}/participants")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.participants[*]['id','user_email','pmi']")
                .build()
            )
            .transform(Transform.Redact.builder()
                // we don't care about user's name
                .jsonPath("$.participants[*]['name','registrant_id']")
                .build())
            .build())
        .build();


    static final Rules2 REPORT_ENDPOINTS = Rules2.builder()
        // List meetings
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/methods#operation/reportMeetings
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/report/users/{accountId}/meetings")
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
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/report/meetings/{meetingId}")
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
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/report/meetings/{meetingId}/participants")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.participants[*]['id','user_email','user_id','pmi']")
                .build()
            )
            .transform(Transform.Redact.builder()
                // we don't care about user's name
                .jsonPath("$.participants[*]['name','registrant_id','display_name']")
                .build())
            .build())
        .build();

    static final Rules2 USERS_ENDPOINTS = Rules2.builder()
        // List users
        // https://marketplace.zoom.us/docs/api-reference/zoom-api/users/users
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/users")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.users[*]['email','phone_number']")
                .build()
            )
            .transform(Transform.Pseudonymize.builder()
                .includeReversible(true) // need to reverse when requesting meetings by user to iterate
                .jsonPath("$.users[*]['id','pmi']")
                .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                .build()
            )
            .transform(Transform.Redact.builder()
                // we don't care about names, profile pic, employee_unique_id (when SSO info)
                .jsonPath("$.users[*]['display_name','first_name','last_name','pic_url','employee_unique_id']")
                .build()
            )
            .build())
        // Get user settings
        // https://developers.zoom.us/docs/api/users/#tag/users/GET/users/{userId}/settings
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/users/{userId}/settings")
            .transform(Transform.Redact.builder()
                // As fields are in different sub-objects, we need to specify each one
                .jsonPath("$..ip_addresses_or_ranges")
                .jsonPath("$..require_password_for_pmi_meetings")
                .jsonPath("$..pmi_password")
                .jsonPath("$..meeting_password_requirement")
                .jsonPath("$..virtual_background_settings")
                .jsonPath("$..audio_conference_info")
                .jsonPath("$..numbers")
                .jsonPath("$..custom_service_instructions")
                .jsonPath("$..default_password_for_scheduled_meetings")
                .build())
            .build()).build();

    static final Rules2 RECORDINGS_ENDPOINTS = Rules2.builder()
        // List user recordings
        //https://developers.zoom.us/docs/api/meetings/#tag/cloud-recording/GET/users/{userId}/recordings
        .endpoint(Endpoint.builder()
            .pathTemplate("/v2/users/{userId}/recordings")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.meetings[*]['host_id','account_id']")
                .build())
            .transform(Transform.Redact.builder()
                .jsonPath("$..['topic','share_url','recording_play_passcode']")
                .jsonPath("$..recording_files[*]['download_url','file_path','play_url','agenda']")
                .build())
            .build()
        ).build();

    static final Rules2 ZOOM = USERS_ENDPOINTS
        .withAdditionalEndpoints(MEETINGS_ENDPOINTS.getEndpoints())
        .withAdditionalEndpoints(REPORT_ENDPOINTS.getEndpoints())
        .withAdditionalEndpoints(RECORDINGS_ENDPOINTS.getEndpoints());


    static public final Map<String, RESTRules> ZOOM_PREBUILT_RULES_MAP = ImmutableMap.<String, RESTRules>builder()
        .put("zoom", ZOOM)
        .build();
}
