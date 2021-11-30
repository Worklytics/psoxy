package co.worklytics.psoxy.rules.google;

import com.google.common.collect.ImmutableMap;
import co.worklytics.psoxy.Rules;
import static co.worklytics.psoxy.Rules.Rule;


import java.util.Map;

/**
 * Prebuilt sanitization rules for Google tools
 */
public class PrebuiltSanitizerRules {

    static final Rules GCAL = Rules.builder()
        .allowedEndpointRegex("^/calendar/v3/calendars/[^/]*?/events.*")
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("/calendar/v3/calendars/.*/events.*")
            .jsonPath("$..email")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("/calendar/v3/calendars/.*/events.*")
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
            .relativeUrlRegex("/admin/directory/v1/users.*")
            .jsonPath("$..primaryEmail")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("/admin/directory/v1/users.*")
            .jsonPath("$..name")
            .jsonPath("$..thumbnailPhotoUrl")
            .jsonPath("$..recoveryEmail")
            .jsonPath("$..recoveryPhone")
            .build()
        )
        // users list
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("/admin/directory/v1/users?.*")
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
            .relativeUrlRegex("/admin/directory/v1/users?.*")
            .jsonPath("$.users[*].posixAccounts[*].homeDirectory")
            .jsonPath("$.users[*].sshPublicKeys[*]")
            .build()
        )
        // single user
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("/admin/directory/v1/users/.*")
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
                .relativeUrlRegex("/admin/directory/v1/users/.*")
                .jsonPath("$.posixAccounts[*].homeDirectory")
                .jsonPath("$.sshPublicKeys[*]")
                .build()
        )
        //group/groups/group members
        .redaction(
            Rule.builder()
                .relativeUrlRegex("/admin/directory/v1/groups.*")
                .jsonPath("$..description")
                .build()
        )
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("/admin/directory/v1/groups/.*?/members.*")
            .jsonPath("$..email")
            .build()
        )

        //  group email/aliases aren't PII, but we need to match them against pseudonymized data,
        //  so need p
        .pseudonymizationWithOriginal(Rule.builder()
            .relativeUrlRegex("/admin/directory/v1/groups.*")
            .jsonPath("$.email")
            .jsonPath("$.aliases[*]")
            .jsonPath("$.nonEditableAliases[*]")
            .jsonPath("$.groups[*].email")
            .jsonPath("$.groups[*].aliases[*]")
            .jsonPath("$.groups[*].nonEditableAliases[*]")
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
            .relativeUrlRegex("/drive/v2/files.*")
            .jsonPath("$..emailAddress")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("/drive/v2/files.*")
            .jsonPath("$..displayName")
            .jsonPath("$..picture")
            .jsonPath("$.files[*].lastModifyingUserName")
            .jsonPath("$.files[*].ownerNames")
            .build())
        .build();

    static final Rules GMAIL = Rules.builder()
       .allowedEndpointRegex("^/gmail/v1/users/[^/]*?/messages.*")
       //cases that we expect may contain a comma-separated list of values, per RFC 2822
       .emailHeaderPseudonymization(Rules.Rule.builder()
                  .relativeUrlRegex("/gmail/v1/users/.*?/messages/.*")
                  .jsonPath("$.payload.headers[?(@.name =~ /^from$/i)].value")
                  .jsonPath("$.payload.headers[?(@.name =~ /^to$/i)].value")
                  .jsonPath("$.payload.headers[?(@.name =~ /^cc$/i)].value")
                  .jsonPath("$.payload.headers[?(@.name =~ /^bcc$/i)].value")
               .build())
       //cases that we expect to be truly single-valued, per RFC 2822
       .pseudonymization(Rules.Rule.builder()
               .relativeUrlRegex("/gmail/v1/users/.*?/messages/.*")
               .jsonPath("$.payload.headers[?(@.name =~ /^X-Original-Sender$/i)].value")
               .jsonPath("$.payload.headers[?(@.name =~ /^Delivered-To$/i)].value")
               .jsonPath("$.payload.headers[?(@.name =~ /^Sender$/i)].value")
               .jsonPath("$.payload.headers[?(@.name =~ /^In-Reply-To$/i)].value")
           .build()
       )
       .redaction(Rules.Rule.builder()
               .relativeUrlRegex("/gmail/v1/users/.*?/messages/.*")
               .jsonPath("$.payload.headers[?(@.name =~ /^Subject$/i)]")
               .jsonPath("$.payload.headers[?(@.name =~ /^Received$/i)]")
               .jsonPath("$.payload.headers[?(@.name =~ /^Return-Path$/i)]")
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
            .relativeUrlRegex("/admin/reports/v1/activity/users/all/applications/meet.*")
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
