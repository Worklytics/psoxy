package co.worklytics.psoxy;

import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import co.worklytics.psoxy.rules.RuleSet;
import lombok.*;

import java.io.Serializable;
import java.net.URL;

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
     * sanitize jsonResponse received from url, according any options set on Sanitizer
     */
    String sanitize(String httpMethod, URL url, String jsonResponse);


    Pseudonymizer getPseudonymizer();

    RESTRules getRules();

}
