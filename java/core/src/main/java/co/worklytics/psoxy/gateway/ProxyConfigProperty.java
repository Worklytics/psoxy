package co.worklytics.psoxy.gateway;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * config properties that control basic proxy behavior
 */
@NoArgsConstructor
@AllArgsConstructor
public enum ProxyConfigProperty implements ConfigService.ConfigProperty {


    /**
     * CUSTOM_RULES_SHA - sha of custom rules file, if custom rules configured via some method
     * *other* than environment vars; this is used to force re-deploy/restart of Cloud Functions
     * / Lambdas, so that new rules are seen by the proxy.
     *
     * It's exposed here to application code, to allow us to confirm that custom rules are indeed
     * being used.
     */
    CUSTOM_RULES_SHA,

    /**
     * OPTIONAL; if omitted, domains are preserved (pass through to clients)
     *
     *  - PRESERVE (default)
     *  - ENCRYPT
     *  - TOKENIZE
     *  - REDACT
     */
    EMAIL_DOMAIN_HANDLING,

    /**
     * 'STRICT', 'IGNORE_DOTS', ...
     *
     * OPTIONAL; default to 'STRICT'; possibly will change in next proxy version.
     */
    EMAIL_CANONICALIZATION,

    /**
     * where to find configuration parameters that are shared across connectors
     * OPTIONAL; default to ""
     */
    PATH_TO_SHARED_CONFIG,

    /**
     * where to find configuration parameters that are specific to this instance
     * OPTIONAL; default to ""
     */
    PATH_TO_INSTANCE_CONFIG,

    PSOXY_ENCRYPTION_KEY(SupportedSource.ENV_VAR_OR_REMOTE),

    ENCRYPTION_KEY_IP(SupportedSource.ENV_VAR_OR_REMOTE),


    /**
     * key used to encrypt the domains of email addresses; use case is to support rotation of this
     * key independent of that used for PII
     *
     * alpha
     */
    ENCRYPTION_KEY_EMAIL_DOMAINS(SupportedSource.ENV_VAR_OR_REMOTE),

    /**
     * default SALT value, used when computing hashes
     */
    PSOXY_SALT(SupportedSource.ENV_VAR_OR_REMOTE),

    /**
     * if set, used instead of PSOXY_SALT when hashing IP addresses; this is to allow distinct rotation schedule, as IPs may not be considered PII
     * *alpha*; we may remove this
     */
    SALT_IP(SupportedSource.ENV_VAR_OR_REMOTE),

    /**
     * if set, used instead of PSOXY_SALT when hashing email domains; this is to allow distinct rotation schedule, as domains are not PII
     * *alpha*; we may remove this
     */
    SALT_EMAIL_DOMAINS(SupportedSource.ENV_VAR_OR_REMOTE),

    //see PseudonymImplementation
    //use case: use `v0.3` if your initially used a `v0.3.x` version of Proxy to collect data and
    // want data collected through a later Proxy version to be pseudonymized consistently with that
    //NOTE: only useful in bulk case, to force legacy pseudonyms there; in API case, should be
    // controlled via header
    PSEUDONYM_IMPLEMENTATION,

    //if relying on default rules, whether to use version that pseudonymizes per-account source IDs
    // that aren't email addresses (eg, the IDs that sources generate for each account, which aren't
    // usually PII without having access to the source's dataset)
    PSEUDONYMIZE_APP_IDS,

    // if set, a base64-YAML encoding of rules
    RULES(SupportedSource.ENV_VAR_OR_REMOTE),

    // for testing - if set, allows for behavior that should only be permitted in development context,
    // such as to skip sanitizer if corresponding header is sent
    IS_DEVELOPMENT_MODE,

    /**
     * **ALPHA**
     * if provided, a target side output to write original data to
     */
    SIDE_OUTPUT_ORIGINAL,

    /**
     * **ALPHA**
     * if provided, a target side output to write sanitized data to
     */
    SIDE_OUTPUT_SANITIZED,

    SOURCE,
    SOURCE_AUTH_STRATEGY_IDENTIFIER,
    //target API endpoint to forward request to
    TARGET_HOST,

    /**
     * control the TLS protocol version used by proxy for outbound connections (eg, to data source)
     * OPTIONAL; default to 'TLSv1.3'
     * only safe alternative setting would be 'TLSv1.2'; we provide option to configure this in
     * case there is some API supported by proxy that doesn't support TLSv1.3 (we're not aware of
     * any as of Sept 2024)
     */
    TLS_VERSION,

    BUNDLE_FILENAME,

    ;

    public static class TlsVersions {
        public final static String TLSv1_2 = "TLSv1.2";
        public final static String TLSv1_3 = "TLSv1.3";
        public final  static String[] ALL = {TLSv1_2, TLSv1_3};
    }


    @Getter(onMethod_ = @Override)
    private  SupportedSource supportedSource = SupportedSource.ENV_VAR;
}
