package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.GoogleCloudPlatformServiceAccountKeyAuthStrategy;
import co.worklytics.psoxy.gateway.impl.OAuthAccessTokenSourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.PrebuiltSanitizerRules;
import co.worklytics.psoxy.rules.RulesUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.java.Log;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * quick hack of DI, pending integration of DI framework (Dagger2) which would replace this (TBD)
 */
@Log
public class DependencyFactoryImpl implements DependencyFactory {


    /**
     * default value for salt; provided just to support testing with minimal config, but in prod
     * use should be overridden with something
     */
    //TODO: these don't belong in this class
    static final String DEFAULT_SALT = "salt";
    static final String PATH_TO_RULES_FILES = "/rules.yaml";

    @Getter
    ConfigService config  = new EnvVarsConfigService();
    @Getter
    ObjectMapper objectMapper = new ObjectMapper();

    @Getter
    RulesUtils rulesUtils = new RulesUtils();

    SourceAuthStrategy sourceAuthStrategy;

    @Override
    public SourceAuthStrategy getSourceAuthStrategy() {
        if (sourceAuthStrategy == null) {
            String identifier = getConfig().getConfigPropertyOrError(ProxyConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER);
            Stream<SourceAuthStrategy> implementations = Stream.of(
                new GoogleCloudPlatformServiceAccountKeyAuthStrategy(),
                new OAuthRefreshTokenSourceAuthStrategy(),
                new OAuthAccessTokenSourceAuthStrategy()
            );
            sourceAuthStrategy = implementations
                .filter(impl -> Objects.equals(identifier, impl.getConfigIdentifier()))
                .findFirst().orElseThrow(() -> new Error("No SourceAuthStrategy impl matching configured identifier: " + identifier));
        }
        return sourceAuthStrategy;
    }

    Sanitizer sanitizer;

    @Override
    public Sanitizer getSanitizer() {
        if (sanitizer == null) {
            Optional<Rules> fileSystemRules = getRulesUtils().getRulesFromFileSystem(PATH_TO_RULES_FILES);
            if (fileSystemRules.isPresent()) {
                log.info("using rules from file system");
            }
            Rules rules = fileSystemRules.orElseGet(() -> {
                Optional<Rules> configRules = getRulesUtils().getRulesFromConfig(getConfig());
                if (configRules.isPresent()) {
                    log.info("using rules from environment config (RULES variable parsed as base64-encoded YAML)");
                }
                return configRules.orElseGet(() -> {
                    String source = getConfig().getConfigPropertyOrError(ProxyConfigProperty.SOURCE);
                    log.info("using prebuilt rules for: " + source);
                    return PrebuiltSanitizerRules.MAP.get(source);
                });
            });

            sanitizer = new SanitizerImpl(
                Sanitizer.Options.builder()
                    .rules(rules)
                    .pseudonymizationSalt(getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                        .orElse(DEFAULT_SALT))
                    .defaultScopeId(getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.IDENTIFIER_SCOPE_ID)
                        .orElse(rules.getDefaultScopeIdForSource()))
                    .build());

            if (getConfig().isDevelopment()) {
                log.warning("Proxy instance configured in development mode (env var IS_DEVELOPMENT_MODE=true)");
            }
        }
        return sanitizer;
    }
}
