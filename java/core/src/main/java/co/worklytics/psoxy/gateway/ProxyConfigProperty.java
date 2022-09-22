package co.worklytics.psoxy.gateway;

/**
 * config properties that control basic proxy behavior
 */
public enum ProxyConfigProperty implements ConfigService.ConfigProperty {
    PSOXY_ENCRYPTION_KEY,

    @Deprecated //removed from v0.4
    IDENTIFIER_SCOPE_ID,

    // salt used to generate pseudonyms
    PSOXY_SALT,

    //if relying on default rules, whether to use version that pseudonymizes per-account source IDs
    // that aren't email addresses (eg, the IDs that sources generate for each account, which aren't
    // usually PII without having access to the source's dataset)
    PSEUDONYMIZE_APP_IDS,

    /**
     * specify how to handle domains when pseudonymizing email addresses
     * {@link com.avaulta.gateway.pseudonyms.EmailDomainPolicy} to use when sanitizing email addresses
     */
    EMAIL_DOMAIN_POLICY,

    /**
     * if defined, domain-portion of any email that matches this CSV list, will be exempted from
     * EMAIL_DOMAIN_POLICY.  The mailbox portion of the email will still be handled as usual.
     *
     * eg, if policy is HASH, but 'acme.com' is in the list:
     *   alice@acme.com --> p~2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90@acme.com
     */
    EMAIL_DOMAIN_POLICY_EXCEPTIONS,

    // if set, a base64-YAML encoding of rules
    RULES,
    // for testing - if set, allows for behavior that should only be permitted in development context,
    // such as to skip sanitizer if corresponding header is sent
    IS_DEVELOPMENT_MODE,
    SOURCE,
    SOURCE_AUTH_STRATEGY_IDENTIFIER,
    //target API endpoint to forward request to
    TARGET_HOST,
    ;
}
