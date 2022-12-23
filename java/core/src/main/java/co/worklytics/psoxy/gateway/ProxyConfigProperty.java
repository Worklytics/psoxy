package co.worklytics.psoxy.gateway;

/**
 * config properties that control basic proxy behavior
 */
public enum ProxyConfigProperty implements ConfigService.ConfigProperty {
    PSOXY_ENCRYPTION_KEY,

    @Deprecated //removed from v0.4
    IDENTIFIER_SCOPE_ID,
    PSOXY_SALT,
    // If this customer email provider considers emails with dots as equivalent to emails without
    // f.e. Gmail -> John.Smith@gmail.com == johnsmith@gmail.com
    IGNORED_DOTS_ON_EMAILS,
    // if IGNORED_DOTS_ON_EMAILS set, the list of domains to apply the rule
    CUSTOMER_DOMAINS,
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
    ;
}
