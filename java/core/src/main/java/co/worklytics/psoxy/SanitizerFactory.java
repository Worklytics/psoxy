package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.impl.SanitizerImpl;

import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface SanitizerFactory {

    SanitizerImpl create(Sanitizer.Options options);

    //q: right place? mapping config+rules --> Sanitizer.Options isn't implementation-specific
    default Sanitizer.Options buildOptions(ConfigService config, Rules rules) {
        return Sanitizer.Options.builder()
            .rules(rules)
                    .pseudonymizationSalt(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                        .orElseThrow(() -> new Error("Must configure value for SALT to generate pseudonyms")))
            .defaultScopeId(config.getConfigPropertyAsOptional(ProxyConfigProperty.IDENTIFIER_SCOPE_ID)
                        .orElse(rules.getDefaultScopeIdForSource()))
            .build();
    }

}
