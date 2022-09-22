package co.worklytics.psoxy;

import com.avaulta.gateway.pseudonyms.EmailDomainPolicy;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import co.worklytics.psoxy.rules.RuleSet;
import lombok.*;

import java.io.Serializable;
import java.net.URL;
import java.util.Optional;
import java.util.Set;

public interface Sanitizer {

    /**
     * immutable sanitizer options
     */
    @With
    @Builder
    @Value
    class ConfigurationOptions implements Serializable {

        private static final long serialVersionUID = 4L;

        /**
         * salt used to generate pseudonyms
         *
         * q: split this out? it's per-customer, not per-source API (although it *could* be the
         * later, if you don't need to match with it)
         */
        String pseudonymizationSalt;

        /**
         * scope to use where logic + rules don't imply a match
         */
        @Deprecated
        String defaultScopeId;

        RuleSet rules;

        @NonNull
        @Builder.Default
        PseudonymImplementation pseudonymImplementation = PseudonymImplementation.DEFAULT;

        @NonNull
        @Builder.Default
        EmailDomainPolicy emailDomainPolicy = EmailDomainPolicy.PRESERVE;

        /**
         * if set, sanitizer should PRESERVE domains matching any of this list
         */
        Set<String> emailDomainPolicyExceptions;

        public Optional<Set<String>> getEmailDomainPolicyExceptions() {
            return Optional.ofNullable(emailDomainPolicyExceptions);
        }
    }

    /**
     * when domains are redacted from email addresses, rather than preserved or pseudonymized, this
     * placeholder is used to preserver format (.domain is a legal TLD per RFC, but does not exist
     * as of 2022-09)
     */
    String REDACTED_DOMAIN = "redacted.domain";


    /**
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
    boolean isAllowed(URL url);


    /**
     * sanitize jsonResponse received from url, according any options set on Sanitizer
     */
    String sanitize(URL url, String jsonResponse);



    /**
     * @param identifier to pseudonymize
     * @return identifier as a PseudonymizedIdentity
     */
    PseudonymizedIdentity pseudonymize(String identifier);

    /**
     * @param identifier to pseudonymize
     * @return identifier as a PseudonymizedIdentity
     */
    PseudonymizedIdentity pseudonymize(Number identifier);

    ConfigurationOptions getConfigurationOptions();
}
