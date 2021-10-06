package co.worklytics.psoxy;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class PrebuiltSanitizerRules {

   static final Sanitizer.Rules GMAIL_V1 = Sanitizer.Rules.builder()
       .pseudonymization(Sanitizer.Rules.Rule.builder()
                  .relativeUrlRegex("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*")
                   .jsonPath("$.payload.headers[?(@.name in ['To','TO','to','From','FROM','from','cc','CC','bcc','BCC','X-Original-Sender','Delivered-To'])].value")
               .build())
          .redaction(Sanitizer.Rules.Rule.builder()
               .relativeUrlRegex("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*")
               .jsonPath("$.payload.headers[?(@.name in ['Subject', 'Received'])]")
               .build())
           .build();


   static final Sanitizer.Rules GOOGLE_CHAT = Sanitizer.Rules.builder()
       .pseudonymization(Sanitizer.Rules.Rule.builder()
           .relativeUrlRegex("\\/admin\\/reports\\/v1\\/activity\\/users\\/all\\/applications\\/chat.*")
               .jsonPath("$.items[*].actor.email")
               .jsonPath("$.items[*].events[*].parameters[?(@.name in ['actor'])].value")
           .build()
       )
       .build();

   static final Sanitizer.Rules GDIRECTORY = Sanitizer.Rules.builder()
       .build();

    static final Sanitizer.Rules GCAL = Sanitizer.Rules.builder()
        .build();



    static public final Map<String, Sanitizer.Rules> MAP = ImmutableMap.<String, Sanitizer.Rules>builder()
        .put("gcal", GCAL)
        .put("gdirectory", GDIRECTORY)
        .put("gmail", GMAIL_V1)
        .put("google-chat", GOOGLE_CHAT)
        .build();
}
