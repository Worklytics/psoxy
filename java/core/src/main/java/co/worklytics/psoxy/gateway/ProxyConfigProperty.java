package co.worklytics.psoxy.gateway;

/**
 * config properties that control basic proxy behavior
 */
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
     * where to find configuration parameters that are shared across connectors
     * OPTIONAL; default to ""
     */
    PATH_TO_SHARED_CONFIG,
    /**
     * where to find configuration parameters that are specific to this instance
     * OPTIONAL; default to ""
     */
    PATH_TO_INSTANCE_CONFIG,

    PSOXY_ENCRYPTION_KEY,
    ENCRYPTION_KEY_IP,


    @Deprecated //removed from v0.4
    IDENTIFIER_SCOPE_ID,


    PSOXY_SALT,
    SALT_IP, // used to salt IP; distinct value so can independently rotate IP salt from primary salt


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
    RULES,
    // for testing - if set, allows for behavior that should only be permitted in development context,
    // such as to skip sanitizer if corresponding header is sent
    IS_DEVELOPMENT_MODE,
    SOURCE,
    SOURCE_AUTH_STRATEGY_IDENTIFIER,
    //target API endpoint to forward request to
    TARGET_HOST,
    BUNDLE_FILENAME;
}
