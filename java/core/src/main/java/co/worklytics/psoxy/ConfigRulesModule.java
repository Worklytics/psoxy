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

    public static final String NO_APP_IDS_SUFFIX = "_no-app-ids";


    @Provides
    static RuleSet rules(Logger log, RulesUtils rulesUtils, ConfigService config) {

        BiFunction<Optional<RuleSet>, String, Optional<RuleSet>> loadAndLog = (o, msg) -> {
            if (o.isPresent()) {
                log.info(msg);
            }
            return o;
        };



        return loadAndLog.apply(rulesUtils.getRulesFromConfig(config),"Rules: loaded from environment config (RULES variable parsed as base64-encoded YAML)")
            .or( () -> loadAndLog.apply(getDefaults(log, config), "Rules: fallback to prebuilt rules"))
                .orElseThrow( () -> new RuntimeException("No rules found"));

    }

    static Optional<RuleSet> getDefaults(Logger log, ConfigService config) {
        boolean pseudonymizeAppIds =
            config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYMIZE_APP_IDS)
                .map(Boolean::parseBoolean).orElse(false);

        String source = config.getConfigPropertyOrError(ProxyConfigProperty.SOURCE);

        String rulesIdSuffix = pseudonymizeAppIds ? NO_APP_IDS_SUFFIX : "";

        RuleSet regularDefaults = PrebuiltSanitizerRules.DEFAULTS.get(source);

        return Optional.ofNullable(PrebuiltSanitizerRules.DEFAULTS.getOrDefault(source + rulesIdSuffix, regularDefaults));
    }
}
