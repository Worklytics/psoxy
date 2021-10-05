package co.worklytics.psoxy;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Map;

public class PrebuiltSanitizerOptions {

   static final Sanitizer.Options GMAIL_V1 = Sanitizer.Options.builder()
         .pseudonymization(Pair.of("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*",
           Arrays.asList(
               "$.payload.headers[?(@.name in ['To','TO','to','From','FROM','from','cc','CC','bcc','BCC','X-Original-Sender','Delivered-To'])].value"
           )))
        .redaction(Pair.of("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*",
           Arrays.asList(
               "$.payload.headers[?(@.name in ['Subject', 'Received'])]"
           )))
        .build();

   static final Sanitizer.Options GOOGLE_CHAT = Sanitizer.Options.builder()
       .pseudonymization(Pair.of("\\/admin\\/reports\\/v1\\/activity\\/users\\/all\\/applications\\/chat.*",
           Arrays.asList(
               "$.items[*].actor.email",
               "$.items[*].events[*].parameters[?(@.name in ['actor'])].value"
           )))
       .build();


    static public final Map<String, Sanitizer.Options> MAP = ImmutableMap.<String, Sanitizer.Options>builder()
        .put("gmail", GMAIL_V1)
        .put("google-chat", GOOGLE_CHAT)
        .build();
}
