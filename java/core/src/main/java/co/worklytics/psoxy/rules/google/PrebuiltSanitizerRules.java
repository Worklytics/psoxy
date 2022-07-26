package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.Rules1;
import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Transform;
import co.worklytics.psoxy.rules.zoom.ZoomTransforms;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prebuilt sanitization rules for Google tools
 */
public class PrebuiltSanitizerRules {

    static final Rules2 GCAL = Rules2.builder()
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/calendar/v3/calendars/[^/]*?$")
            .transform(Transform.Redact.ofPaths("$.summary"))
            .transform(Transform.Pseudonymize.ofPaths("$.id"))
            .build())
        .endpoint(Rules2.Endpoint.builder()
             .pathRegex("^/calendar/v3/calendars/[^/]*?/events[^/]*")
            .transform(Transform.Pseudonymize.ofPaths("$..email"))
            .transform(Transform.Redact.ofPaths(
                "$..displayName",
                "$.summary",
                "$.items[*].extendedProperties.private",
                "$.items[*].summary"
            ))
            .transform(ZoomTransforms.FILTER_CONTENT_EXCEPT_ZOOM_URL.toBuilder()
                .jsonPath("$.items[*].description")
                .build())
            .transform(ZoomTransforms.SANITIZE_JOIN_URL.toBuilder()
                .jsonPath("$.items[*].description")
                .build())
            .build())
        .endpoint( Rules2.Endpoint.builder()
            .pathRegex("^/calendar/v3/calendars/[^/]*?/events/.*")
            .transform(Transform.Redact.ofPaths(
                "$..displayName",
                "$.summary"
            ))
            .transform(ZoomTransforms.FILTER_CONTENT_EXCEPT_ZOOM_URL.toBuilder()
                .jsonPath("$.description")
                .build())
            .transform(ZoomTransforms.SANITIZE_JOIN_URL.toBuilder()
                .jsonPath("$.description")
                .build())
            .transform(Transform.Pseudonymize.ofPaths("$..email"))
            .build())
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/calendar/v3/users/[^/]*?/settings.*")
            .build())
        .build();

    static final Set<String> GOOGLE_CHAT_EVENT_PARAMETERS_PII = ImmutableSet.of(
        "actor"
    );
    static final Set<String> GOOGLE_CHAT_EVENT_PARAMETERS_ALLOWED = ImmutableSet.<String>builder()
        .addAll(GOOGLE_CHAT_EVENT_PARAMETERS_PII)
        .add("room_id", "timestamp_ms", "message_id", "room_name")
        .build();


    static final RuleSet GOOGLE_CHAT = Rules2.builder()
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/reports/v1/activity/users/all/applications/chat.*$")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$..email")
                .jsonPath("$.items[*].events[*].parameters[?(@.name in [" +
                    GOOGLE_CHAT_EVENT_PARAMETERS_PII.stream().map(s -> "'" + s + "'").collect(Collectors.joining(",")) +
                    "])].value")
                .build())
            .transform(Transform.Redact.builder()
                .jsonPath("$.items[*].events[*].parameters[?(!(@.name =~ /^" +
                    String.join("|", GOOGLE_CHAT_EVENT_PARAMETERS_ALLOWED) +
                    "$/i))]")
                .build())
            .build())
        .build();

    static final Rules2 GDIRECTORY = Rules2.builder()
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/directory/v1/customer/[^/]*/domains.*")
            .build())
        //list users
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/directory/v1/users\\?.*$")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.users[*].primaryEmail")
                .jsonPath("$.users[*].emails[*].address")
                .jsonPath("$.users[*].externalIds[*].value")
                .jsonPath("$.users[*].aliases[*]")
                .jsonPath("$.users[*].nonEditableAliases[*]")
                .jsonPath("$.users[*].ims[*].im")
                .jsonPath("$.users[*].phones[*].value")
                .jsonPath("$.users[*].posixAccounts[*].accountId")
                .jsonPath("$.users[*].posixAccounts[*].uid")
                .jsonPath("$.users[*].posixAccounts[*].username")
                .jsonPath("$.users[*].locations[*].deskCode")
                .jsonPath("$.users[*].relations[*].value")
                .build())
            .transform(Transform.Redact.builder()
                .jsonPath("$.users[*].name")
                .jsonPath("$.users[*].thumbnailPhotoUrl")
                .jsonPath("$.users[*].recoveryEmail")
                .jsonPath("$.users[*].recoveryPhone")
                .jsonPath("$.users[*].posixAccounts[*].homeDirectory")
                .jsonPath("$.users[*].sshPublicKeys[*]")
                .jsonPath("$.users[*].websites[*]")
                .build())
            .build())
        //single user
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/directory/v1/users/[^/]*$")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.primaryEmail")

                .jsonPath("$.emails[*].address")
                .jsonPath("$.aliases[*]")
                .jsonPath("$.nonEditableAliases[*]")
                .jsonPath("$.ims[*].im")
                .jsonPath("$.externalIds[*].value")
                .jsonPath("$.phones[*].value")
                .jsonPath("$.posixAccounts[*].accountId")
                .jsonPath("$.posixAccounts[*].uid")
                .jsonPath("$.posixAccounts[*].username")
                .jsonPath("$.locations[*].deskCode")
                .jsonPath("$.relations[*].value")
                .build())
            .transform(Transform.Redact.builder()
                .jsonPath("$.name")
                .jsonPath("$.thumbnailPhotoUrl")
                .jsonPath("$.recoveryEmail")
                .jsonPath("$.recoveryPhone")
                .jsonPath("$.posixAccounts[*].homeDirectory")
                .jsonPath("$.sshPublicKeys[*]")
                .jsonPath("$.websites[*]")
                .build())
            .build())
        //list groups
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/directory/v1/groups(\\?)?[^/]*$")
            .transform(Transform.Pseudonymize.builder()
                .includeOriginal(true)
                .jsonPath("$..email")
                .jsonPath("$..aliases[*]")
                .jsonPath("$..nonEditableAliases[*]")
                .build()
            )
            .transform(Transform.Redact.builder()
                 .jsonPath("$..description")
                .build())
            .build())
        //single group
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/directory/v1/groups/[^/]*$")
            .transform(Transform.Pseudonymize.builder()
                .includeOriginal(true)
                .jsonPath("$..email")
                .jsonPath("$..aliases[*]")
                .jsonPath("$..nonEditableAliases[*]")
                .build()
            )
            .transform(Transform.Redact.builder()
                .jsonPath("$..description")
                .build())
            .build())
        //list group members
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/directory/v1/groups/[^/]*/members[^/]*$")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$..email")
                .jsonPath("$..aliases[*]")
                .jsonPath("$..nonEditableAliases[*]")
                .build())
            .build())
        //list org units
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/directory/v1/orgunits\\?.*")
            .transform(Transform.Redact.builder()
                .jsonPath("$..description")
                .build())
            .build())
        //get org unit
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/directory/v1/orgunits/[^/]*")
            .transform(Transform.Redact.builder()
                .jsonPath("$..description")
                .build())
            .build())

        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/directory/v1/customer/[^/]*/roles[^/]*")
            .transform(Transform.Redact.builder()
                .jsonPath("$..roleDescription")
                .build())
            .build())
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/directory/v1/customer/[^/]*/roles/[^/]*")
            .transform(Transform.Redact.builder()
                .jsonPath("$..roleDescription")
                .build())
            .build())
        //TODO: roles/roleassignments/resources
        //.endpoint(Rules2.Endpoint.builder()
        //    .pathRegex("^/admin/directory/v1/customer/my_customer/(roles|roleassignments|resources).*")
        //    .build())
        .build();

    public static co.worklytics.psoxy.rules.Rules2.Endpoint GDRIVE_ENDPOINT_RULES = Rules2.Endpoint.builder()
        .transform(Transform.Pseudonymize.builder()
            .jsonPath("$..emailAddress")
            .build())
        .transform(Transform.Redact.builder()
            .jsonPath("$..name")
            .jsonPath("$..photoLink")
            .jsonPath("$..title")
            .jsonPath("$..description")
            .jsonPath("$..originalFilename") // defensive about file recognition, anywhere
            .jsonPath("$..displayName") //user display name, anywhere (confidentiality)
            .jsonPath("$..picture") //user picture, anywhere (confidentiality)
            .jsonPath("$..lastModifyingUserName")
            .jsonPath("$..ownerNames")
            .build())
        .build();


    static final RuleSet GDRIVE = Rules2.builder()
        // v2 endpoint: https://developers.google.com/drive/api/v2/reference/
        // v3 endpoint: https://developers.google.com/drive/api/v3/reference/
        .endpoint(GDRIVE_ENDPOINT_RULES.toBuilder()
            .pathRegex("^/drive/v[2,3]/files[/]?[^/]*")
            .build())
        .endpoint(GDRIVE_ENDPOINT_RULES.toBuilder()
            .pathRegex("^/drive/v[2,3]/files/[^/]*/revisions[/]?[^/]*")
            .build())
        .endpoint(GDRIVE_ENDPOINT_RULES.toBuilder()
            .pathRegex("^/drive/v[2,3]/files/[^/]*/permissions.*")
            .build())
        .build();

    //cases that we expect may contain a comma-separated list of values, per RFC 2822
    static Set<String> EMAIL_HEADERS_CONTAINING_MULTIPLE_EMAILS = ImmutableSet.of(
        "From","To","Cc","Bcc"
    );

    //cases that we expect to be truly single-valued, per RFC 2822
    static Set<String> EMAIL_HEADERS_CONTAINING_SINGLE_EMAILS = ImmutableSet.of(
        "X-Original-Sender","Delivered-To","Sender"
    );
    static Set<String> ALLOWED_EMAIL_HEADERS = ImmutableSet.<String>builder()
        .addAll(EMAIL_HEADERS_CONTAINING_MULTIPLE_EMAILS)
        .addAll(EMAIL_HEADERS_CONTAINING_SINGLE_EMAILS)
        .add("Message-ID") // this looks like an email address
        .add("Date")
        .add("In-Reply-To") // this is a Message-ID
        .add("Original-Message-ID")
        .add("References")
        .build();

    static final RuleSet GMAIL = Rules2.builder()
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/gmail/v1/users/[^/]*/messages[/]?.*?$")
            .transform(Transform.PseudonymizeEmailHeader.builder()
                .jsonPath("$.payload.headers[?(@.name =~ /^(" + String.join("|", EMAIL_HEADERS_CONTAINING_MULTIPLE_EMAILS) + ")$/i)].value")
                .build())
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$.payload.headers[?(@.name =~ /^(" + String.join("|", EMAIL_HEADERS_CONTAINING_SINGLE_EMAILS) + ")$/i)].value")
                .build())
            .transform(Transform.Redact.builder()
                // this build a negated JsonPath predicate for all allowed headers, so anything other
                // than expected headers will be redacted.
                .jsonPath("$.payload.headers[?(!(@.name =~ /^" + String.join("|", ALLOWED_EMAIL_HEADERS) + "$/i))]")
                .build())
            .build())
       .build();

    static final Set<String> GOOGLE_MEET_EVENT_PARAMETERS_PII = ImmutableSet.of(
        "organizer_email",
        "ip_address",
        "identifier"
    );

    static final Set<String> GOOGLE_MEET_EVENT_PARAMETERS_ALLOWED = ImmutableSet.<String>builder()
        .addAll(GOOGLE_MEET_EVENT_PARAMETERS_PII)
        .add("location_country", "location_region", "ip_address") //collaboration across offices / geographies / time zones
        .add("is_external") // internal v external collaboration
        .add("product_type", "device_type") // tool classification
        .add("video_send_seconds", "video_recv_seconds", "screencast_send_seconds", "screencast_recv_seconds", "audio_send_seconds", "audio_recv_seconds") //actual duration inference
        .add("calendar_event_id", "endpoint_id", "meeting_code", "conference_id") //matching to calendar events
        .build();

    static final RuleSet GOOGLE_MEET = Rules2.builder()
        .endpoint(Rules2.Endpoint.builder()
            .pathRegex("^/admin/reports/v1/activity/users/all/applications/meet.*")
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$..email")
                .jsonPath("$.items[*].events[*].parameters[?(@.name in [" +
                    GOOGLE_MEET_EVENT_PARAMETERS_PII.stream().map(s -> "'" + s + "'").collect(Collectors.joining(",")) +
                    "])].value")
                .build())
            .transform(Transform.Redact.builder()
                // this build a negated JsonPath predicate for all allowed event parameters, so anything other
                // than expected headers will be redacted. Important to keep ".*$" at the end.
                .jsonPath("$.items[*].events[*].parameters[?(!(@.name =~ /^" +
                    String.join("|", GOOGLE_MEET_EVENT_PARAMETERS_ALLOWED) +
                    "$/i))]")
                .build())
            .build())
        .build();

    static public final Map<String, RuleSet> GOOGLE_DEFAULT_RULES_MAP = ImmutableMap.<String, RuleSet>builder()
        .put("gcal", GCAL)
        .put("gdirectory", GDIRECTORY)
        .put("gdrive", GDRIVE)
        .put("gmail", GMAIL)
        .put("google-chat", GOOGLE_CHAT)
        .put("google-meet", GOOGLE_MEET)
        .build();
}
