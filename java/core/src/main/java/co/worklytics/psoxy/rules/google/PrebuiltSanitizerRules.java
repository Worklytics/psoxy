package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.generics.Calendar;
import com.avaulta.gateway.rules.Endpoint;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.transforms.Transform;
import co.worklytics.psoxy.rules.zoom.ZoomTransforms;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prebuilt sanitization rules for Google tools
 */
public class PrebuiltSanitizerRules {

    static final Rules2 GCAL = Rules2.builder()
        .endpoint(Endpoint.builder()
            .pathTemplate("/calendar/v3/calendars/{accountId}")
            .transform(Transform.Redact.ofPaths("$.summary"))
            .transform(Transform.Pseudonymize.ofPaths("$.id"))
            .build())
        .endpoint(Endpoint.builder()
            .pathTemplate("/calendar/v3/calendars/{accountId}/events")
            .transform(Transform.Pseudonymize.ofPaths("$..email"))
            .transform(Transform.Redact.ofPaths(
                "$.summary",
                "$..displayName",
                "$.items[*].extendedProperties", // overly conservative, but we just don't know what's here
                "$.items[*].conferenceData.notes",
                "$.items[*].conferenceData.entryPoints[*]['accessCode','password','passcode','pin']",
                "$..meetingCreatedBy" // not documented; seen for one customer; would be better to pseudonymize, if has reliable schema
            ))
            .transform(Calendar.PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS.toBuilder()
                .jsonPath("$.items[*].summary")
                .build())
            .transform(ZoomTransforms.FILTER_CONTENT_EXCEPT_ZOOM_URL.toBuilder()
                .jsonPath("$.items[*].description")
                .build())
            .transform(ZoomTransforms.SANITIZE_JOIN_URL.toBuilder()
                .jsonPath("$.items[*].description")
                .jsonPath("$.items[*].location") // sometimes ppl put meeting url here
                .jsonPath("$.items[*].conferenceData.entryPoints[*].uri")
                .build())
            .build())
        .endpoint( Endpoint.builder()
            .pathTemplate("/calendar/v3/calendars/{accountId}/events/{eventId}")
            .transform(Transform.Redact.ofPaths(
                "$.summary",
                "$..displayName",
                "$.extendedProperties", // overly conservative, but we just don't know what's here
                "$.conferenceData.entryPoints[*]['accessCode','password','passcode','pin']",
                "$.conferenceData.notes",
                "$..meetingCreatedBy" //not documented, but seen for one customer; would be better to pseudonymize, if has reliable schema
            ))
            .transform(Calendar.PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS.toBuilder()
                .jsonPath("$.items[*].summary")
                .build())
            .transform(ZoomTransforms.FILTER_CONTENT_EXCEPT_ZOOM_URL.toBuilder()
                .jsonPath("$.description")
                .build())
            .transform(ZoomTransforms.SANITIZE_JOIN_URL.toBuilder()
                .jsonPath("$.description")
                .jsonPath("$.conferenceData.entryPoints[*].uri")
                .jsonPath("$.location") // sometimes ppl put meeting url here
                .build())
            .transform(Transform.Pseudonymize.builder()
                .jsonPath("$..email")
                .build())
            .build())
        .endpoint(Endpoint.builder()
            .pathTemplate("/calendar/v3/users/{accountId}/settings")
            .build())
        //calendarList needed to analyze historical calendars
        .endpoint(Endpoint.builder()
            .pathTemplate("/calendar/v3/users/{accountId}/calendarList")
            .transform(Transform.FilterTokenByRegex.builder().jsonPath("$.items[*].summaryOverride")
                .jsonPath("$.items[*].summary")
                .filter("Transferred").build())
            .transform(Transform.Pseudonymize.builder().jsonPath("$.items[*].id").includeReversible(true).encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN).build())
            .build())
        .build();

    static final Set<String> GOOGLE_CHAT_EVENT_PARAMETERS_PII = ImmutableSet.of(
            "actor"
    );
    static final Set<String> GOOGLE_CHAT_EVENT_PARAMETERS_ALLOWED = ImmutableSet.<String>builder()
            .addAll(GOOGLE_CHAT_EVENT_PARAMETERS_PII)
            .add("room_id", "timestamp_ms", "message_id", "room_name")
            .build();


    static final RESTRules GOOGLE_CHAT = Rules2.builder()
            .endpoint(Endpoint.builder()
                .pathTemplate("/admin/reports/v1/activity/users/all/applications/chat")
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


    static final String GDIRECTORY_ENDPOINT_TEMPLATE_USERS = "/admin/directory/v1/users";
    static final String GDIRECTORY_ENDPOINT_TEMPLATE_USER = "/admin/directory/v1/users/{accountId}";
    static final String GDIRECTORY_ENDPOINT_TEMPLATE_MEMBERS = "/admin/directory/v1/groups/{groupId}/members";


    static final Rules2 GDIRECTORY = Rules2.builder()
            .endpoint(Endpoint.builder()
                .pathTemplate("/admin/directory/v1/customer/{customerId}/domains")
                    .build())
            //list users
            .endpoint(Endpoint.builder()
                    .pathTemplate(GDIRECTORY_ENDPOINT_TEMPLATE_USERS)
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
                            .jsonPath("$.users[*].organizations[*].title") // job title sometimes identifying
                            .jsonPath("$.users[*].posixAccounts[*].homeDirectory")
                            .jsonPath("$.users[*].recoveryEmail")
                            .jsonPath("$.users[*].recoveryPhone")
                            .jsonPath("$.users[*].sshPublicKeys[*]")
                            .jsonPath("$.users[*].thumbnailPhotoUrl")
                            .jsonPath("$.users[*].websites[*]")
                            .build())
                    .build())
            //single user
            .endpoint(Endpoint.builder()
                    .pathTemplate(GDIRECTORY_ENDPOINT_TEMPLATE_USER)
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
                            .jsonPath("$.organizations[*].title")
                            .jsonPath("$.posixAccounts[*].homeDirectory")
                            .jsonPath("$.recoveryEmail")
                            .jsonPath("$.recoveryPhone")
                            .jsonPath("$.sshPublicKeys[*]")
                            .jsonPath("$.thumbnailPhotoUrl")
                            .jsonPath("$.websites[*]")
                            .build())
                    .build())
            //list groups
            .endpoint(Endpoint.builder()
                .pathTemplate("/admin/directory/v1/groups")
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
            .endpoint(Endpoint.builder()
                    .pathTemplate("/admin/directory/v1/groups/{groupId}")
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
            .endpoint(Endpoint.builder()
                    .pathTemplate(GDIRECTORY_ENDPOINT_TEMPLATE_MEMBERS)
                    .transform(Transform.Pseudonymize.builder()
                            .jsonPath("$..email")
                            .jsonPath("$..aliases[*]")
                            .jsonPath("$..nonEditableAliases[*]")
                            .build())
                    .build())
            //list org units
            // https://developers.google.com/admin-sdk/directory/reference/rest/v1/orgunits/list
            .endpoint(Endpoint.builder()
                .pathTemplate("/admin/directory/v1/customer/{customerId}/orgunits")
                    .transform(Transform.Redact.builder()
                            .jsonPath("$..description")
                            .build())
                    .build())
            //get org unit
            .endpoint(Endpoint.builder()
                .pathTemplate("/admin/directory/v1/customer/{customerId}/orgunits/{orgUnitPath}")
                    .transform(Transform.Redact.builder()
                            .jsonPath("$..description")
                            .build())
                    .build())
            .build();

    public static final Rules2 GDIRECTORY_WITHOUT_GOOGLE_IDS = GDIRECTORY
            .withTransformByEndpointTemplate(GDIRECTORY_ENDPOINT_TEMPLATE_USER,
                    Transform.Pseudonymize.builder()
                            .jsonPath("$.id")
                            .includeReversible(true)
                            .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                            .build())
            .withTransformByEndpointTemplate(GDIRECTORY_ENDPOINT_TEMPLATE_USERS,
                    Transform.Pseudonymize.builder()
                            .jsonPath("$.users[*].id")
                            .includeReversible(true)
                            .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                            .build())
            .withTransformByEndpointTemplate(GDIRECTORY_ENDPOINT_TEMPLATE_MEMBERS,
                    Transform.Pseudonymize.builder()
                            .jsonPath("$.members[*].id")
                            .build());

    public static Endpoint GDRIVE_ENDPOINT_RULES = Endpoint.builder()
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


    // as of Apr 2023, Worklytics uses v2 GDrive API; can migrate to v3, but haven't yet - so both
    // endpoints are open
    static final RESTRules GDRIVE = Rules2.builder()
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
            "From", "To", "Cc", "Bcc"
    );

    //cases that we expect to be truly single-valued, per RFC 2822
    static Set<String> EMAIL_HEADERS_CONTAINING_SINGLE_EMAILS = ImmutableSet.of(
            "X-Original-Sender", "Delivered-To", "Sender"
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


    static final RESTRules GMAIL = Rules2.builder()
            .endpoint(Endpoint.builder()
                .pathTemplate("/gmail/v1/users/{mailboxId}/messages")
                .transform(
                    Transform.PseudonymizeEmailHeader.builder()
                        .jsonPath("$.messages.payload.headers[?(@.name =~ /^(" + String.join("|", EMAIL_HEADERS_CONTAINING_MULTIPLE_EMAILS) + ")$/i)].value")
                        .build())
                .transform(
                    Transform.Pseudonymize.builder()
                        .jsonPath("$.messages.payload.headers[?(@.name =~ /^(" + String.join("|", EMAIL_HEADERS_CONTAINING_SINGLE_EMAILS) + ")$/i)].value")
                        .build())
                .transform(
                    Transform.Redact.builder()
                        // this build a negated JsonPath predicate for all allowed headers, so anything other
                        // than expected headers will be redacted.
                        .jsonPath("$.messages.payload.headers[?(!(@.name =~ /^" + String.join("|", ALLOWED_EMAIL_HEADERS) + "$/i))]")
                        .build())
            .build())
            .endpoint(Endpoint.builder()
                .pathTemplate("/gmail/v1/users/{mailboxId}/messages/{messageId}")
                .transform(
                    Transform.PseudonymizeEmailHeader.builder()
                    .jsonPath("$.payload.headers[?(@.name =~ /^(" + String.join("|", EMAIL_HEADERS_CONTAINING_MULTIPLE_EMAILS) + ")$/i)].value")
                    .build())
                .transform(
                    Transform.Pseudonymize.builder()
                    .jsonPath("$.payload.headers[?(@.name =~ /^(" + String.join("|", EMAIL_HEADERS_CONTAINING_SINGLE_EMAILS) + ")$/i)].value")
                    .build())
                .transform(
                    Transform.Redact.builder()
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
            .add("identifier_type") // id type (email) which can be used to identity the user
            .add("location_country", "location_region", "ip_address") //collaboration across offices / geographies / time zones
            .add("is_external") // internal v external collaboration
            .add("product_type", "device_type") // tool classification
            .add("video_send_seconds", "video_recv_seconds", "screencast_send_seconds", "screencast_recv_seconds", "audio_send_seconds", "audio_recv_seconds") //actual duration inference
            .add("calendar_event_id", "endpoint_id", "meeting_code", "conference_id") //matching to calendar events
            .build();

    static final RESTRules GOOGLE_MEET = Rules2.builder()
            .endpoint(Endpoint.builder()
                .pathTemplate("/admin/reports/v1/activity/users/all/applications/meet")
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

    static final  RESTRules GEMINI_FOR_WORKSPACE =
        Rules2.load("sources/google-workspace/gemini-for-workspace/gemini-for-workspace.yaml");
    static final RESTRules GEMINI_FOR_WORKSPACE_NO_APP_IDS =
        Rules2.load("sources/google-workspace/gemini-for-workspace/gemini-for-workspace_no-app-ids.yaml");

    static public final Map<String, RESTRules> GOOGLE_DEFAULT_RULES_MAP = ImmutableMap.<String, RESTRules>builder()
            .put("gcal", GCAL)
            .put("gdirectory", GDIRECTORY)
            .put("gdirectory" + ConfigRulesModule.NO_APP_IDS_SUFFIX, GDIRECTORY_WITHOUT_GOOGLE_IDS)
            .put("gdrive", GDRIVE)
            .put("gmail", GMAIL)
            .put("google-chat", GOOGLE_CHAT)
            .put("google-meet", GOOGLE_MEET)
            .put("gemini-for-workspace", GEMINI_FOR_WORKSPACE)
            .put("gemini-for-workspace" + ConfigRulesModule.NO_APP_IDS_SUFFIX, GEMINI_FOR_WORKSPACE_NO_APP_IDS)
            .build();
}
