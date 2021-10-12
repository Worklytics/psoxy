package co.worklytics.psoxy.rules.google;

import com.google.common.collect.ImmutableMap;
import co.worklytics.psoxy.Rules;
import static co.worklytics.psoxy.Rules.Rule;


import java.util.Map;

public class PrebuiltSanitizerRules {

    static final Rules GCAL = Rules.builder()
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/calendar\\/v3\\/calendars\\/.*\\/events.*")
            .jsonPath("$.items[*].creator.email")
            .jsonPath("$.items[*].organizer.email")
            .jsonPath("$.items[*].attendees[*].email")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/calendar\\/v3\\/calendars\\/.*\\/events.*")
            .jsonPath("$.items[*].creator.displayName")
            .jsonPath("$.items[*].organizer.displayName")
            .jsonPath("$.items[*].attendees[*].displayName")
            .jsonPath("$.items[*].extendedProperties.private")
            .build())
        .build();


    static final Rules GOOGLE_CHAT = Rules.builder()
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/admin\\/reports\\/v1\\/activity\\/users\\/all\\/applications\\/chat.*")
            .jsonPath("$.items[*].actor.email")
            .jsonPath("$.items[*].events[*].parameters[?(@.name in ['actor'])].value")
            .build()
        )
        .build();

    static final Rules GDIRECTORY = Rules.builder()
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/admin\\/directory\\/v1\\/users\\?.*")
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
            .build()
        )
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/admin\\/directory\\/v1\\/users\\/.*")
            .jsonPath("$.primaryEmail")
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
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/admin\\/directory\\/v1\\/users\\?.*")
            .jsonPath("$.users[*].name")
            .jsonPath("$.users[*].thumbnailPhotoUrl")
            .jsonPath("$.users[*].posixAccounts[*].homeDirectory")
            .jsonPath("$.users[*].sshPublicKeys[*]")
            .jsonPath("$.users[*].recoveryEmail")
            .jsonPath("$.users[*].recoveryPhone")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/admin\\/directory\\/v1\\/users\\/.*")
            .jsonPath("$.name")
            .jsonPath("$.thumbnailPhotoUrl")
            .jsonPath("$.posixAccounts[*].homeDirectory")
            .jsonPath("$.sshPublicKeys[*]")
            .jsonPath("$.recoveryEmail")
            .jsonPath("$.recoveryPhone")
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
            .relativeUrlRegex("\\/drive\\/v2\\/files.*")
            .jsonPath("$..emailAddress")
            .build())
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/drive\\/v2\\/files.*")
            .jsonPath("$..displayName")
            .jsonPath("$..picture")
            .jsonPath("$.files[*].lastModifyingUserName")
            .jsonPath("$.files[*].ownerNames")
            .build())
        .build();

    static final Rules GMAIL = Rules.builder()
       //cases that we expect may contain a comma-separated list of values, per RFC 2822
       .emailHeaderPseudonymization(Rules.Rule.builder()
                  .relativeUrlRegex("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*")
                  .jsonPath("$.payload.headers[?(@.name in ['To','TO','to','From','FROM','from','cc','CC','bcc','BCC'])].value")
               .build())
       //cases that we expect to be truly single-valued, per RFC 2822
       .pseudonymization(Rules.Rule.builder()
               .relativeUrlRegex("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*")
               .jsonPath("$.payload.headers[?(@.name in ['X-Original-Sender','Delivered-To','Sender'])].value")
           .build()
       )
       .redaction(Rules.Rule.builder()
               .relativeUrlRegex("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*")
               .jsonPath("$.payload.headers[?(@.name in ['Subject', 'Received'])]")
               .build()
       )
       .build();

    static final Rules GOOGLE_MEET = Rules.builder()
        .pseudonymization(Rule.builder()
            .relativeUrlRegex("\\/admin\\/reports\\/v1\\/activity\\/users\\/all\\/applications\\/meet.*")
            .jsonPath("$.items[*].actor.email")
            .jsonPath("$.items[*].events[*].parameters[?(@.name in ['ip_address', 'organizer_email', 'identifier'])].value")
            .build()
        )
        .redaction(Rule.builder()
            .relativeUrlRegex("\\/admin\\/reports\\/v1\\/activity\\/users\\/all\\/applications\\/meet.*")
            .jsonPath("$.items[*].actor.email")
            .jsonPath("$.items[*].events[*].parameters[?(@.name in ['display_name'])].value")
            .build()
        )
        .build();


    static public final Map<String, Rules> MAP = ImmutableMap.<String, Rules>builder()
        .put("gcal", GCAL)
        .put("gdirectory", GDIRECTORY)
        .put("gdrive", GDRIVE)
        .put("gmail", GMAIL)
        .put("google-chat", GOOGLE_CHAT)
        .put("google-meet", GOOGLE_MEET)
        .build();
}
