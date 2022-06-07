package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.impl.SanitizerImpl;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules1;
import co.worklytics.psoxy.rules.Rules2;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface SanitizerFactory {

    SanitizerImpl create(Sanitizer.Options options);

    //q: right place? mapping config+rules --> Sanitizer.Options isn't implementation-specific
    default Sanitizer.Options buildOptions(ConfigService config, RuleSet rules) {
        Sanitizer.Options.OptionsBuilder builder = Sanitizer.Options.builder()
            .pseudonymizationSalt(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                .orElseThrow(() -> new Error("Must configure value for SALT to generate pseudonyms")))
            .defaultScopeId(config.getConfigPropertyAsOptional(ProxyConfigProperty.IDENTIFIER_SCOPE_ID)
                .orElse(rules.getDefaultScopeIdForSource()));

        if (rules instanceof Rules1) {
            builder.rules((Rules1) rules);
        } else {
            builder.rules2((Rules2) rules);
        }
        return builder.build();
    }

}
