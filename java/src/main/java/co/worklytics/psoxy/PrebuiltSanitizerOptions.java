package co.worklytics.psoxy;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;

public class PrebuiltSanitizerOptions {

    static final Sanitizer.Options GMAIL_V1 = Sanitizer.Options.builder()
        .pseudonymization(
            Pair.of("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*",
                Arrays.asList(
                    "$.payload.headers[?(@.name in ['To','TO','to','From','FROM','from','cc','CC','bcc','BCC'])].value")))
        .build();
}
