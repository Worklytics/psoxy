package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.RESTApiSanitizerImpl;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public interface RESTApiSanitizer {
    /**
     * @param httpMethod to test
     * @param url to test
     * @return whether url is even allowed to be called via proxy, given Sanitizer rule set
     *
     *         q: some scope question about whether this is beyond Sanitizer responsibility or not
     *         args for:
     *         - it's an interpretation of rules
     *         - correct if 'santizer' is sanitizing an "API" rather than "API response content"
     *         - sanitization can't really be decoupled from the semantics of the endpoints in
     *         question
     *         args against:
     *         - could split this into two classes, with one that deals with endpoint level stuff,
     *         such
     *         as 1) what endpoints allowed, and 2) what pseudonymizations and redactions to apply
     *         to response given the endpoint; and the other(s) which just do the
     *         pseudonymization/redaction (eg, pseudonymize(jsonPAths, content); redact(jsonPaths,
     *         content))
     *         - just invariably that's quite coupled, per above
     */
    boolean isAllowed(String httpMethod, URL url);

    /**
     * @param httpMethod to test
     * @param url to test
     * @param contentType to test
     * @param requestBody to test
     * @return whether url is even allowed to be called via proxy, given Sanitizer rule set
     */
    boolean isAllowed(String httpMethod, URL url, String contentType, String requestBody);

    /**
     * Headers to include in the request
     * 
     * @param httpMethod The method to test
     * @param url The url to test
     * @return
     */
    Optional<Collection<String>> getAllowedHeadersToForward(String httpMethod, URL url);

    /**
     * sanitize jsonResponse received from url, according any options set on Sanitizer
     */
    String sanitize(String httpMethod, URL url, String jsonResponse);


    Pseudonymizer getPseudonymizer();

    RESTRules getRules();

    /**
     * sanitize response stream received from url, according any options set on Sanitizer
     * <p>
     * bc of streaming interface, this is preferred when expect large responses
     * <p>
     * q: compression; do we return gzipped stream out of here, or have consumer choose that??
     *
     * @param httpMethod
     * @param url
     * @param response
     * @return
     * @throws IOException
     */
    RESTApiSanitizerImpl.ProcessedStream sanitize(String httpMethod, URL url, InputStream response)
            throws IOException;

    @RequiredArgsConstructor
    class ProcessedStream {

        @Getter
        private final InputStream stream;
        private final Future<?> future;

        public void complete() throws ExecutionException, InterruptedException {
            future.get();
        }
    }
}
