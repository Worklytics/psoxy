package co.worklytics.psoxy;

import co.worklytics.psoxy.rules.RESTRules;

import java.net.URL;
import java.util.Collection;
import java.util.Optional;

public interface RESTApiSanitizer {
    /**
     * @param httpMethod to test
     * @param url to test
     * @return whether url is even allowed to be called via proxy, given Sanitizer rule set
     *
     * q: some scope question about whether this is beyond Sanitizer responsibility or not
     *   args for:
     *      - it's an interpretation of rules
     *      - correct if 'santizer' is sanitizing an "API" rather than "API response content"
     *      - sanitization can't really be decoupled from the semantics of the endpoints in question
     *   args against:
     *      - could split this into two classes, with one that deals with endpoint level stuff, such
     *        as 1) what endpoints allowed, and 2) what pseudonymizations and redactions to apply
     *        to response given the endpoint; and the other(s) which just do the
     *        pseudonymization/redaction (eg, pseudonymize(jsonPAths, content); redact(jsonPaths, content))
     *        - just invariably that's quite coupled, per above
     */
    boolean isAllowed(String httpMethod, URL url);

    /**
     * Headers to include in the request
     * @param httpMethod The method to test
     * @param url The url to test
     * @return
     */
    Optional<Collection<String>> getSupportedHeaders(String httpMethod, URL url);

    /**
     * sanitize jsonResponse received from url, according any options set on Sanitizer
     */
    String sanitize(String httpMethod, URL url, String jsonResponse);


    Pseudonymizer getPseudonymizer();

    RESTRules getRules();

}