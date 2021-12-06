package co.worklytics.psoxy.gateway;

/**
 * config properties that control basic proxy behavior
 */
public enum ProxyConfigProperty implements ConfigService.ConfigProperty {
    IDENTIFIER_SCOPE_ID,
    PSOXY_SALT,
    // if set, a base64-YAML encoding of rules
    RULES,
    // for testing - if set, allows proxy to skip sanitizer if corresponding header is sent
    SKIP_SANITIZER, //q: better to just have a IS_TEST_ENV variable?
    SOURCE,
    SOURCE_AUTH_STRATEGY_IDENTIFIER,
    //target API endpoint to forward request to
    TARGET_HOST,
}
