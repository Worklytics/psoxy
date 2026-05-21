package co.worklytics.psoxy;

import java.io.InputStream;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Singleton;
import com.avaulta.gateway.rules.BulkDataRules;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.RecordRules;
import com.avaulta.gateway.rules.RuleSet;
import com.google.common.base.Preconditions;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.ResourceService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.rules.PrebuiltSanitizerRules;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.RulesUtils;
import dagger.Module;
import dagger.Provides;

@Module
public class ConfigRulesModule {

    public static final String NO_APP_IDS_SUFFIX = "_no-app-ids";

    /**
     * Well-known resource path for rules loaded from InstanceResourceService (local FS or remote
     * cloud storage).
     */
    public static final String RULES_RESOURCE_PATH = "rules.yaml";

    @Provides @Singleton
    static RESTRules restRules(RuleSet ruleSet) {
        if (! (ruleSet instanceof RESTRules)) {
            // will blow things up if something that depends on RESTRules is bound in flat file use-case
            throw new RuntimeException("Configured RuleSet are not RESTRules");
        }
        return (RESTRules) ruleSet;
    }

    @Provides @Singleton
    static BulkDataRules bulkDataRules(RuleSet ruleSet) {
        // will blow things up if something that depends on ColumnarRules is bound in REST-usecase
        Preconditions.checkArgument(ruleSet instanceof BulkDataRules, "Configured RuleSet are not BulkDataRules");

        return (BulkDataRules) ruleSet;
    }

    @Provides @Singleton
    static RecordRules recordRules(RuleSet ruleSet) {
        if (!(ruleSet instanceof RecordRules)) {
            // will blow things up if something that depends on ColumnarRules is bound in REST-usecase
            throw new RuntimeException("Configured RuleSet are not RecordRules");
        }
        return (RecordRules) ruleSet;
    }


    @Provides @Singleton
    static ColumnarRules columnarRules(RuleSet ruleSet) {
        if (!(ruleSet instanceof ColumnarRules)) {
            // will blow things up if something that depends on ColumnarRules is bound in REST-usecase
            throw new RuntimeException("Configured RuleSet are not ColumnarRules");
        }

        return (ColumnarRules) ruleSet;
    }



    @Provides @Singleton
    static RuleSet rules(Logger log,
                         RulesUtils rulesUtils,
                         ConfigService config,
                         EnvVarsConfigService envVarsConfigService,
                         ResourceService resourceService) {

        BiFunction<Optional<RuleSet>, String, Optional<RuleSet>> loadAndLog = (o, msg) -> {
            if (o.isPresent()) {
                log.info(msg);
            }
            return o;
        };

        return loadAndLog.apply(rulesUtils.getRulesFromConfig(config, envVarsConfigService), "Rules: loaded from environment config (RULES variable parsed as base64-encoded YAML)")
            .or( () -> loadAndLog.apply(getRulesFromResource(log, rulesUtils, resourceService), "Rules: loaded from instance resource (" + RULES_RESOURCE_PATH + ")"))
            .or( () -> loadAndLog.apply(getDefaults(log, config), "Rules: fallback to prebuilt rules"))
                .orElseThrow( () -> new RuntimeException("No rules found"));

    }

    /**
     * Attempt to load rules from the InstanceResourceService (local FS or remote S3/GCS bucket).
     */
    static Optional<RuleSet> getRulesFromResource(Logger log, RulesUtils rulesUtils, ResourceService resourceService) {
        try {
            if (resourceService.exists(RULES_RESOURCE_PATH)) {
                return Optional.empty();
            }

            try (InputStream is = rulesStream.get()) {
                String yamlContent = new String(is.readAllBytes());
                return Optional.of(rulesUtils.parse(yamlContent));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to load rules from resource service", e);
            return Optional.empty();
        }
    }

    //NOTE: not compile error due to type-erasure, but DEFAULTS are all RESTRules
    static Optional<RuleSet> getDefaults(Logger log, ConfigService config) {
        boolean pseudonymizeAppIds =
            config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYMIZE_APP_IDS)
                .map(Boolean::parseBoolean).orElse(false);

        String source = config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE)
            .orElseThrow( () -> new RuntimeException(
                String.format("No source specified, so can't determine default rules. Configure '%s' or '%s' to avoid this error.",
                    ProxyConfigProperty.SOURCE.name(), ProxyConfigProperty.RULES.name())));

        String rulesIdSuffix = pseudonymizeAppIds ? NO_APP_IDS_SUFFIX : "";

        RESTRules regularDefaults = PrebuiltSanitizerRules.DEFAULTS.get(source);

        //ok to fallback to regular rules, bc for many sources the 'NO_APP_IDS' variant doesn't
        // really matter
        return Optional.ofNullable(PrebuiltSanitizerRules.DEFAULTS.getOrDefault(source + rulesIdSuffix, regularDefaults));
    }
}
