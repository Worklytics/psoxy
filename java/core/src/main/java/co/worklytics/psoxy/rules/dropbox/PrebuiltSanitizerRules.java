package co.worklytics.psoxy.rules.dropbox;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

/**
 * Prebuilt sanitization rules for Dropbox API responses
 */
public class PrebuiltSanitizerRules {

    static private final List<String> DEFAULT_QUERY_HEADERS = List.of("Dropbox-API-Select-User");

    static private final String DROPBOX_BUSINESS = "dropbox-business";

    // https://www.dropbox.com/developers/documentation/http/teams#team_log-get_events
    // https://www.dropbox.com/developers/documentation/http/teams#team_log-get_events-continue
    static final Rules2 EVENT_LIST = Events("^/2/team_log/get_events(/continue)?$");

    // https://www.dropbox.com/developers/documentation/http/documentation#files-list_folder
    // https://www.dropbox.com/developers/documentation/http/documentation#files-list_folder-continue
    static final Rules2 FILE_LIST = Metadata("^/2/files/list_folder(/continue)?$");

    // https://www.dropbox.com/developers/documentation/http/documentation#files-list_revisions
    static final Rules2 FILE_REVISIONS = Metadata("^/2/files/list_revisions$");

    // https://www.dropbox.com/developers/documentation/http/teams#team-groups-members-list
    // https://www.dropbox.com/developers/documentation/http/teams#team-groups-members-list-continue
    static final Rules2 GROUP_LIST_ENDPOINT = MemberProfile("^/2/team/groups/members/list(/continue)?$");

    // https://www.dropbox.com/developers/documentation/http/teams#team-members-list
    // https://www.dropbox.com/developers/documentation/http/teams#team-members-list-continue
    static final Rules2 MEMBER_LIST_ENDPOINT = MemberProfile("^/2/team/members/list(/continue)?_v2$");

    static final Rules2 DROPBOX_ENDPOINTS = EVENT_LIST
            .withAdditionalEndpoints(FILE_LIST.getEndpoints())
            .withAdditionalEndpoints(FILE_REVISIONS.getEndpoints())
            .withAdditionalEndpoints(GROUP_LIST_ENDPOINT.getEndpoints())
            .withAdditionalEndpoints(MEMBER_LIST_ENDPOINT.getEndpoints());

    static public final Map<String, RESTRules> DROPBOX_PREBUILT_RULES_MAP = ImmutableMap.<String, RESTRules>builder()
            .put(DROPBOX_BUSINESS, DROPBOX_ENDPOINTS)
            .build();

    static private Rules2 MemberProfile(String pathRegex) {
        return Rules2.builder()
                .endpoint(Endpoint.builder()
                        .pathRegex(pathRegex)
                        .allowedRequestHeaders(DEFAULT_QUERY_HEADERS)
                        .transform(Transform.Pseudonymize.builder()
                                .jsonPath("$.members[*].profile.email")
                                .jsonPath("$.members[*].profile.secondary_emails[*].email")
                                .build()
                        )
                        .transform(Transform.Pseudonymize.builder()
                                .jsonPath("$.members[*].profile.account_id")
                                .jsonPath("$.members[*].profile.persistent_id")
                                .build()
                        )
                        .transform(Transform.Redact.builder()
                                .jsonPath("$.members[*].profile.name['abbreviated_name', 'display_name', 'familiar_name', 'given_name', 'surname']")
                                .jsonPath("$.members[*].profile['profile_photo_url']")
                                .jsonPath("$.members[*].roles[*]['description', 'name']")
                                .build()
                        )
                        .build())
                .build();
    }

    static private Rules2 Metadata(String pathRegex) {
        return Rules2.builder()
                .endpoint(Endpoint.builder()
                        .pathRegex(pathRegex)
                        .allowedRequestHeaders(DEFAULT_QUERY_HEADERS)
                        .transform(Transform.Pseudonymize.builder()
                                .jsonPath("$.entries[*].details.shared_content_owner.email")
                                .build()
                        )
                        .transform(Transform.Pseudonymize.builder()
                                .jsonPath("$.entries[*].sharing_info.modified_by")
                                .jsonPath("$.entries[*].file_lock_info.lockholder_account_id")
                                .build()
                        )
                        .transform(Transform.Redact.builder()
                                .jsonPath("$.entries[*]['name', 'path_lower', 'path_display', 'preview_url', 'content_hash']")
                                .jsonPath("$.entries[*].export_info['export_as']")
                                .jsonPath("$.entries[*].file_lock_info['lockholder_name']")
                                .build()
                        )
                        .build())
                .build();
    }

    static private Rules2 Events(String pathRegex) {
        return Rules2.builder()
                .endpoint(Endpoint.builder()
                        .pathRegex(pathRegex)
                        .allowedRequestHeaders(DEFAULT_QUERY_HEADERS)
                        .transform(Transform.Pseudonymize.builder()
                                .jsonPath("$.events[*].details.shared_content_owner.email")
                                .jsonPath("$.events[*].actor.user.email")
                                .jsonPath("$.events[*].actor.admin.email")
                                .jsonPath("$.events[*].actor.reseller.resellerEmail")
                                .jsonPath("$.events[*].origin.geo_location.ip_address")
                                .jsonPath("$.events[*].context.email")
                                .jsonPath("$.events[*].participants[*].user.email")
                                .build()
                        )
                        .transform(Transform.Pseudonymize.builder()
                                .jsonPath("$.events[*].details.shared_content_owner.account_id")
                                .jsonPath("$.events[*].actor.user.account_id")
                                .jsonPath("$.events[*].actor.admin.account_id")
                                .jsonPath("$.events[*].context.account_id")
                                .jsonPath("$.events[*].participants[*].user.account_id")
                                .build()
                        )
                        .transform(Transform.Redact.builder()
                                .jsonPath("$.events[*].details.shared_content_owner['display_name']")
                                .jsonPath("$.events[*].actor.user['display_name']")
                                .jsonPath("$.events[*].actor.admin['display_name']")
                                .jsonPath("$.events[*].actor.reseller.reseller_name")
                                .jsonPath("$.events[*].context['display_name']")
                                .jsonPath("$.events[*].participants[*].user['display_name']")
                                .jsonPath("$.events[*].assets[*]['display_name', 'doc_title', 'folder_title', 'showcase_title']")
                                .jsonPath("$.events[*].assets[*].path['contextual']")
                                .jsonPath("$.events[*].assets[*].path.namespace_relative['relative_path']")
                                .build()
                        )
                        .build())
                .build();
    }
}