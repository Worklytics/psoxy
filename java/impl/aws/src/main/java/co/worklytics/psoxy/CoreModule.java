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
import dagger.Module;
import dagger.Provides;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

//TODO: duplicated in every impl pkg, due to Dagger code generation. need to improve this
//  - component dependencies??
//  - better multi-module build???
//  - migrate to Gradle??
@Module
public interface CoreModule {

    /**
     * default value for salt; provided just to support testing with minimal config, but in prod
     * use should be overridden with something
     */
    //TODO: these don't belong in this class
    String DEFAULT_SALT = "salt";
    String PATH_TO_RULES_FILES = "/rules.yaml";

    @Provides
    static EnvVarsConfigService envVarsConfigService() {
        return new EnvVarsConfigService();
    }

    @Provides
    static ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Provides
    static RulesUtils rulesUtils() {
        return new RulesUtils();
    }

    @Provides
    static SourceAuthStrategy getSourceAuthStrategy(ConfigService config) {
            String identifier = config.getConfigPropertyOrError(ProxyConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER);
            Stream<SourceAuthStrategy> implementations = Stream.of(
                new GoogleCloudPlatformServiceAccountKeyAuthStrategy(),
                new OAuthRefreshTokenSourceAuthStrategy(),
                new OAuthAccessTokenSourceAuthStrategy()
            );
           return implementations
                .filter(impl -> Objects.equals(identifier, impl.getConfigIdentifier()))
                .findFirst().orElseThrow(() -> new Error("No SourceAuthStrategy impl matching configured identifier: " + identifier));
    }

    @Provides
    static Logger logger() {
        return Logger.getLogger(CoreModule.class.getCanonicalName());
    }

    @Provides
    static Sanitizer getSanitizer(Logger log, ConfigService config, RulesUtils rulesUtils) {
            Optional<Rules> fileSystemRules = rulesUtils.getRulesFromFileSystem(PATH_TO_RULES_FILES);
            if (fileSystemRules.isPresent()) {
                log.info("using rules from file system");
            }
            Rules rules = fileSystemRules.orElseGet(() -> {
                Optional<Rules> configRules = rulesUtils.getRulesFromConfig(config);
                if (configRules.isPresent()) {
                    log.info("using rules from environment config (RULES variable parsed as base64-encoded YAML)");
                }
                return configRules.orElseGet(() -> {
                    String source = config.getConfigPropertyOrError(ProxyConfigProperty.SOURCE);
                    log.info("using prebuilt rules for: " + source);
                    return PrebuiltSanitizerRules.MAP.get(source);
                });
            });

        SanitizerImpl sanitizer = new SanitizerImpl(
            Sanitizer.Options.builder()
                .rules(rules)
                .pseudonymizationSalt(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                    .orElse(DEFAULT_SALT))
                .defaultScopeId(config.getConfigPropertyAsOptional(ProxyConfigProperty.IDENTIFIER_SCOPE_ID)
                    .orElse(rules.getDefaultScopeIdForSource()))
                .build());

            if (config.isDevelopment()) {
                log.warning("Proxy instance configured in development mode (env var IS_DEVELOPMENT_MODE=true)");
            }

        return sanitizer;
    }


}
