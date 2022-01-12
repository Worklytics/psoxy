package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.rules.PrebuiltSanitizerRules;
import co.worklytics.psoxy.rules.RulesUtils;
import dagger.Module;
import dagger.Provides;

import java.util.Optional;
import java.util.logging.Logger;

@Module
public class ConfigRulesModule {

    public static final String PATH_TO_RULES_FILES = "/rules.yaml";

    @Provides
    static Rules rules(Logger log, RulesUtils rulesUtils, ConfigService config) {
        Optional<Rules> fileSystemRules = rulesUtils.getRulesFromFileSystem(PATH_TO_RULES_FILES);
        if (fileSystemRules.isPresent()) {
            log.info("using rules from file system");
        }
        return fileSystemRules.orElseGet(() -> {
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
    }
}
