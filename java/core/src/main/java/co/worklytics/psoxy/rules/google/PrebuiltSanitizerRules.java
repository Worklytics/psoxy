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

import static co.worklytics.psoxy.rules.Rules1.Rule;

/**
 * Prebuilt sanitization rules for Google tools
 */
public class PrebuiltSanitizerRules {

    static final Rules2 GCAL_2 = Rules2.builder()
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


    static final Rules1 GOOGLE_CHAT = Rules1.builder()
        .allowedEndpointRegex("^/admin/reports/v1/activity/users/all/applications/chat.*")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("^/admin/reports/v1/activity/users/all/applications/chat.*")
            .jsonPath("$..email")
            .jsonPath("$.items[*].events[*].parameters[?(@.name in [" +
                GOOGLE_CHAT_EVENT_PARAMETERS_PII.stream().map(s -> "'" + s + "'").collect(Collectors.joining(",")) +
                "])].value")
            .build()
        )
        .redaction(Rules1.Rule.builder()
            .relativeUrlRegex("^/admin/reports/v1/activity/users/all/applications/chat.*")
            // this build a negated JsonPath predicate for all allowed event parameters, so anything other
            // than expected headers will be redacted. Important to keep ".*$" at the end.
            .jsonPath("$.items[*].events[*].parameters[?(!(@.name =~ /^" +
                String.join("|", GOOGLE_CHAT_EVENT_PARAMETERS_ALLOWED) +
                "$/i))]")
            .build()
        )
        .build();

    static final Rules1 GDIRECTORY = Rules1.builder()
        //GENERAL stuff
        //to block: https://admin.googleapis.com/admin/directory/v1/users/{userKey}/photos/thumbnail
        .allowedEndpointRegex("^/admin/directory/v1/(groups|orgunits).*")
        .allowedEndpointRegex("^/admin/directory/v1/users\\?.*")
        .allowedEndpointRegex("^/admin/directory/v1/users/[^/]*")
        .allowedEndpointRegex("^/admin/directory/v1/customer/my_customer/(domains|roles|roleassignments|resources).*")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("^/admin/directory/v1/users.*")
            .jsonPath("$..primaryEmail")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("^/admin/directory/v1/users.*")
            .jsonPath("$..name")
            .jsonPath("$..thumbnailPhotoUrl")
            .jsonPath("$..recoveryEmail")
            .jsonPath("$..recoveryPhone")
            .build()
        )
        .redaction(Rule.builder() //not easy to block thumbnails with a regex ...
            .relativeUrlRegex("^/admin/directory/v1/users/.*?/thumbnail.*")
            .jsonPath("$.primaryEmail")
            .jsonPath("$.photoData")
            .build())
        // users list
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("^/admin/directory/v1/users?.*")
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
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("^/admin/directory/v1/users?.*")
            .jsonPath("$.users[*].posixAccounts[*].homeDirectory")
            .jsonPath("$.users[*].sshPublicKeys[*]")
            .jsonPath("$.users[*].websites[*]")
            .build()
        )
        // single user
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("^/admin/directory/v1/users/.*")
            .jsonPath("$.emails[*].address")
            .jsonPath("$.externalIds[*].value")
            .jsonPath("$.aliases[*]")
            .jsonPath("$.nonEditableAliases[*]")
            .jsonPath("$.ims[*].im")
            .jsonPath("$.phones[*].value")
            .jsonPath("$.posixAccounts[*].accountId")
            .jsonPath("$.posixAccounts[*].uid")
            .jsonPath("$.posixAccounts[*].username")
            .jsonPath("$.locations[*].deskCode")
            .jsonPath("$.relations[*].value")
            .build()
        )
        .redaction(
            Rule.builder()
                .relativeUrlRegex("^/admin/directory/v1/users/.*")
                .jsonPath("$.posixAccounts[*].homeDirectory")
                .jsonPath("$.sshPublicKeys[*]")
                .jsonPath("$.websites[*]")
                .build()
        )
        //group/groups/group members
        .redaction(
            Rule.builder()
                .relativeUrlRegex("^/admin/directory/v1/groups.*")
                .jsonPath("$..description")
                .build()
        )
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("^/admin/directory/v1/groups/.*?/members.*")
            .jsonPath("$..email")
            .build()
        )

        //  group email/aliases aren't PII, but we need to match them against pseudonymized data,
        //  so need p
        .pseudonymizationWithOriginal(Rule.builder()
            .relativeUrlRegex("^/admin/directory/v1/groups.*")
            .jsonPath("$.email")
            .jsonPath("$.aliases[*]")
            .jsonPath("$.nonEditableAliases[*]")
            .jsonPath("$.groups[*].email")
            .jsonPath("$.groups[*].aliases[*]")
            .jsonPath("$.groups[*].nonEditableAliases[*]")
            .build()
        )
        .redaction(
            Rule.builder()
                .relativeUrlRegex("^/admin/directory/v1/orgunits.*")
                .jsonPath("$..description")
                .build()
        )
        .redaction(
            Rule.builder()
                .relativeUrlRegex("^/admin/directory/v1/customer/.*/roles.*")
                .jsonPath("$..roleDescription")
                .build()
        )
        .build();

    static final Rules1 GDRIVE = Rules1.builder()
        // v2 endpoint: https://developers.google.com/drive/api/v2/reference/
        // v3 endpoint: https://developers.google.com/drive/api/v3/reference/
        //NOTE: by default, files endpoint doesn't return any PII. client must pass a fields mask
        // that explicitly requests it; so if we could block that behavior, we could eliminate these
        // rules
        //NOTE: reference docs say page elements collection is named 'items', but actual API returns
        // page collection as 'files' or 'revisions'
        .allowedEndpointRegex("^/drive/v2/files.*")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("^/drive/v2/files.*")
            .jsonPath("$..emailAddress")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("^/drive/v2/files.*")
            .jsonPath("$..['name','title','description','originalFilename']") // defensive about file recognition, anywhere
            .jsonPath("$..displayName") //user display name, anywhere (confidentiality)
            .jsonPath("$..picture") //user picture, anywhere (confidentiality)
            .jsonPath("$.lastModifyingUserName")
            .jsonPath("$.items[*].lastModifyingUserName")
            .jsonPath("$.ownerNames")
            .jsonPath("$.items[*].ownerNames")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("^/drive/v2/files/.*?/revisions.*")
            .jsonPath("$.originalFilename")
            .jsonPath("$.items[*].originalFilename") // duplicated with files.*
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("^/drive/v2/files/.*?/permissions.*")
            .jsonPath("$.name") //likely duplicative with files.* case above
            .jsonPath("$.photoLink")
            .jsonPath("$.items[*].name")
            .jsonPath("$.items[*].photoLink")
            .build())
        .allowedEndpointRegex("^/drive/v3/files.*")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("^/drive/v3/files.*")
            .jsonPath("$..emailAddress")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("^/drive/v3/files.*")
            .jsonPath("$..['name','title','description','originalFilename']") // defensive about file recognition, anywhere
            .jsonPath("$..displayName")
            .jsonPath("$..photoLink")
            .jsonPath("$.files[*].name")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("^/drive/v3/files/.*?/revisions.*")
            .jsonPath("$..originalFilename")
            .jsonPath("$.files[*].originalFilename")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("^/drive/v2/files/.*?/permissions.*")
            .jsonPath("$.displayName") //likely duplicative with files.* case above
            .jsonPath("$.photoLink")
            .jsonPath("$.permissions[*].displayName") //likely duplicative with files.* case above
            .jsonPath("$.permissions[*].photoLink")
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

    static final Rules1 GMAIL = Rules1.builder()
       .allowedEndpointRegex("^/gmail/v1/users/[^/]*?/messages.*")
       .emailHeaderPseudonymization(Rules1.Rule.builder()
                  .relativeUrlRegex("^/gmail/v1/users/.*?/messages/.*")
                  .jsonPath("$.payload.headers[?(@.name =~ /^(" + String.join("|", EMAIL_HEADERS_CONTAINING_MULTIPLE_EMAILS) + ")$/i)].value")
                  .build())
       .pseudonymization(Rules1.Rule.builder()
                  .relativeUrlRegex("^/gmail/v1/users/.*?/messages/.*")
                  .jsonPath("$.payload.headers[?(@.name =~ /^(" + String.join("|", EMAIL_HEADERS_CONTAINING_SINGLE_EMAILS) + ")$/i)].value")
                  .build()
       )
       .redaction(Rules1.Rule.builder()
               .relativeUrlRegex("^/gmail/v1/users/.*?/messages/.*")
                // this build a negated JsonPath predicate for all allowed headers, so anything other
                // than expected headers will be redacted.
               .jsonPath("$.payload.headers[?(!(@.name =~ /^" + String.join("|", ALLOWED_EMAIL_HEADERS) + "$/i))]")
               .build()
       )
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

    static final Rules1 GOOGLE_MEET = Rules1.builder()
        .allowedEndpointRegex("^/admin/reports/v1/activity/users/all/applications/meet.*")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("/admin/reports/v1/activity/users/all/applications/meet.*")
            .jsonPath("$..email")
            .jsonPath("$.items[*].events[*].parameters[?(@.name in [" +
                GOOGLE_MEET_EVENT_PARAMETERS_PII.stream().map(s -> "'" + s + "'").collect(Collectors.joining(",")) +
                "])].value")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("^/admin/reports/v1/activity/users/all/applications/meet.*")
            // this build a negated JsonPath predicate for all allowed event parameters, so anything other
            // than expected headers will be redacted. Important to keep ".*$" at the end.
            .jsonPath("$.items[*].events[*].parameters[?(!(@.name =~ /^" +
                String.join("|", GOOGLE_MEET_EVENT_PARAMETERS_ALLOWED) +
                "$/i))]")
            .build()
        )
        .build();

    static public final Map<String, RuleSet> GOOGLE_DEFAULT_RULES_MAP = ImmutableMap.<String, RuleSet>builder()
        .put("gcal", GCAL_2)
        .put("gdirectory", GDIRECTORY)
        .put("gdrive", GDRIVE)
        .put("gmail", GMAIL)
        .put("google-chat", GOOGLE_CHAT)
        .put("google-meet", GOOGLE_MEET)
        .build();
}
