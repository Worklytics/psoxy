package co.worklytics.psoxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.NonNull;

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
     *
     *
     * TODO: migrate to isAllowed(String httpMethod, URL url, String contentType, String requestBody)
     */
    @Deprecated // use isAllowed(String httpMethod, URL url, String contentType, String requestBody) instead, as more general; this version assumes GET/HEAD request method
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
    Optional<Collection<String>> getAllowedRequestHeadersToForward(String httpMethod, URL url);

    /**
     * sanitize jsonResponse received from url, according any options set on Sanitizer
     */
    String sanitize(String httpMethod, URL url, String jsonResponse);


    Pseudonymizer getPseudonymizer();

    RESTRules getRules();

    void sanitize(@NonNull String httpMethod,
                  @NonNull URL url, InputStream originalStream,
                  OutputStream outputStream) throws IOException;

}
