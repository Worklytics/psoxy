package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;

import static co.worklytics.psoxy.Rules.Rule;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;

/**
 * Prebuilt sanitization rules for Google tools
 */
public class PrebuiltSanitizerRules {

    static final Rules GCAL = Rules.builder()
        .allowedEndpointRegex("^/calendar/v3/calendars/[^/]*?/events.*")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("^/calendar/v3/calendars/.*/events.*")
            .jsonPath("$..email")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("^/calendar/v3/calendars/.*/events.*")
            .jsonPath("$..displayName")
            .jsonPath("$.items[*].extendedProperties.private")
            .build())
        .build();

    static final Rules GOOGLE_CHAT = Rules.builder()
        .allowedEndpointRegex("^/admin/reports/v1/activity/users/all/applications/chat.*")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("/admin/reports/v1/activity/users/all/applications/chat.*")
            .jsonPath("$..email")
            .jsonPath("$.items[*].events[*].parameters[?(@.name in ['actor'])].value")
            .build()
        )
        .build();

    static final Rules GDIRECTORY = Rules.builder()
        //GENERAL stuff
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
        .build();

    static final Rules GDRIVE = Rules.builder()
        //NOTE: by default, files endpoint doesn't return any PII. client must pass a fields mask
        // that explicitly requests it; so if we could block that behavior, we could eliminate these
        // rules
        //NOTE: reference docs say page elements collection is named 'items', but actual API returns
        // page collection as 'files' or 'revisions'
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("^/drive/v2/files.*")
            .jsonPath("$..emailAddress")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("^/drive/v2/files.*")
            .jsonPath("$..displayName")
            .jsonPath("$..picture")
            .jsonPath("$.files[*].lastModifyingUserName")
            .jsonPath("$.files[*].ownerNames")
            .build())
        .build();

    //cases that we expect may contain a comma-separated list of values, per RFC 2822
    static Set<String> EMAIL_HEADERS_CONTAINING_MULTIPLE_EMAILS = ImmutableSet.of(
        "From","To","Cc","Bcc"
    );

    //cases that we expect to be truly single-valued, per RFC 2822
    static Set<String> EMAIL_HEADERS_CONTAINING_SINGLE_EMAILS = ImmutableSet.of(
        "X-Original-Sender","Delivered-To","Sender","In-Reply-To"
    );
    static Set<String> ALLOWED_EMAIL_HEADERS = ImmutableSet.<String>builder()
        .addAll(EMAIL_HEADERS_CONTAINING_MULTIPLE_EMAILS)
        .addAll(EMAIL_HEADERS_CONTAINING_SINGLE_EMAILS)
        .add("Message-ID")
        .add("Date")
        .add("In-Reply-To")
        .add("Original-Message-ID")
        .add("References")
        .build();

    static final Rules GMAIL = Rules.builder()
       .allowedEndpointRegex("^/gmail/v1/users/[^/]*?/messages.*")
       .emailHeaderPseudonymization(Rules.Rule.builder()
                  .relativeUrlRegex("^/gmail/v1/users/.*?/messages/.*")
                  .jsonPath("$.payload.headers[?(@.name =~ /^(" + String.join("|", EMAIL_HEADERS_CONTAINING_MULTIPLE_EMAILS) + ")$/i)].value")
                  .build())
       .pseudonymization(Rules.Rule.builder()
                  .relativeUrlRegex("^/gmail/v1/users/.*?/messages/.*")
                  .jsonPath("$.payload.headers[?(@.name =~ /^(" + String.join("|", EMAIL_HEADERS_CONTAINING_SINGLE_EMAILS) + ")$/i)].value")
                  .build()
       )
       .redaction(Rules.Rule.builder()
               .relativeUrlRegex("^/gmail/v1/users/.*?/messages/.*")
                // this build a negated JsonPath predicate for all allowed headers, so anything other
                // than expected headers will be redacted. Important to keep ".*$" at the end.
               .jsonPath("$.payload.headers[?(!(@.name =~ /^" + String.join("|", ALLOWED_EMAIL_HEADERS) + "$/i))]")
               .build()
       )
       .build();

    static final Rules GOOGLE_MEET = Rules.builder()
        .allowedEndpointRegex("^/admin/reports/v1/activity/users/all/applications/meet.*")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("/admin/reports/v1/activity/users/all/applications/meet.*")
            .jsonPath("$..email")
            .jsonPath("$.items[*].events[*].parameters[?(@.name in ['ip_address', 'organizer_email', 'identifier'])].value")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("^/admin/reports/v1/activity/users/all/applications/meet.*")
            .jsonPath("$.items[*].events[*].parameters[?(@.name in ['display_name'])].value")
            .build()
        )
        .build();

    static public final Map<String, Rules> GOOGLE_PREBUILT_RULES_MAP = ImmutableMap.<String, Rules>builder()
        .put("gcal", GCAL)
        .put("gdirectory", GDIRECTORY)
        .put("gdrive", GDRIVE)
        .put("gmail", GMAIL)
        .put("google-chat", GOOGLE_CHAT)
        .put("google-meet", GOOGLE_MEET)
        .build();
}
