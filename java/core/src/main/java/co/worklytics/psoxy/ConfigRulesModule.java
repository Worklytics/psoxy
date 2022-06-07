package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.rules.PrebuiltSanitizerRules;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.RulesUtils;
import dagger.Module;
import dagger.Provides;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Logger;

@Module
public class ConfigRulesModule {

    public static final String PATH_TO_RULES_FILES = "/rules.yaml";

    @Provides
    static RuleSet rules(Logger log, RulesUtils rulesUtils, ConfigService config) {

        BiFunction<Optional<RuleSet>, String, Optional<RuleSet>> loadAndLog = (o, msg) -> {
            if (o.isPresent()) {
                log.info(msg);
            }
            return o;
        };

        return loadAndLog.apply(rulesUtils.getRulesFromFileSystem(PATH_TO_RULES_FILES), "Rules: loaded from file system")
            .or( () -> loadAndLog.apply(rulesUtils.getRulesFromConfig(config),"Rules: loaded from environment config (RULES variable parsed as base64-encoded YAML)"))
            .or( () -> loadAndLog.apply(Optional.ofNullable(
                PrebuiltSanitizerRules.DEFAULTS.get(config.getConfigPropertyOrError(ProxyConfigProperty.SOURCE))), "Rules: fallback to prebuilt rules"))
                .orElseThrow( () -> new RuntimeException("No rules found"));

    }
}
