package co.worklytics.psoxy;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class PrebuiltSanitizerRules {

   static final Sanitizer.Rules GMAIL_V1 = Sanitizer.Rules.builder()
       //cases that we expect may contain a comma-separated list of values, per RFC 2822
       .emailHeaderPseudonymization(Sanitizer.Rules.Rule.builder()
                  .relativeUrlRegex("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*")
                   .jsonPath("$.payload.headers[?(@.name in ['To','TO','to','From','FROM','from','cc','CC','bcc','BCC'])].value")
               .build())
       //cases that we expect to be truly single-valued, per RFC 2822
       .pseudonymization(Sanitizer.Rules.Rule.builder()
               .relativeUrlRegex("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*")
               .jsonPath("$.payload.headers[?(@.name in ['X-Original-Sender','Delivered-To','Sender'])].value")
           .build()
       )
       .redaction(Sanitizer.Rules.Rule.builder()
               .relativeUrlRegex("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*")
               .jsonPath("$.payload.headers[?(@.name in ['Subject', 'Received'])]")
               .build()
       )
       .build();


   static final Sanitizer.Rules GOOGLE_CHAT = Sanitizer.Rules.builder()
       .pseudonymization(Sanitizer.Rules.Rule.builder()
           .relativeUrlRegex("\\/admin\\/reports\\/v1\\/activity\\/users\\/all\\/applications\\/chat.*")
               .jsonPath("$.items[*].actor.email")
               .jsonPath("$.items[*].events[*].parameters[?(@.name in ['actor'])].value")
           .build()
       )
       .build();


    static public final Map<String, Sanitizer.Rules> MAP = ImmutableMap.<String, Sanitizer.Rules>builder()
        .put("gmail", GMAIL_V1)
        .put("google-chat", GOOGLE_CHAT)
        .build();
}
