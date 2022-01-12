package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.impl.SanitizerImpl;

import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface SanitizerFactory {

    String DEFAULT_SALT = "salt";

    SanitizerImpl create(Sanitizer.Options options);

    //q: right place? mapping config+rules --> Sanitizer.Options isn't implementation-specific
    default Sanitizer.Options buildOptions(ConfigService config, Rules rules) {
        return Sanitizer.Options.builder()
            .rules(rules)
                    .pseudonymizationSalt(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                        .orElse(DEFAULT_SALT))
            .defaultScopeId(config.getConfigPropertyAsOptional(ProxyConfigProperty.IDENTIFIER_SCOPE_ID)
                        .orElse(rules.getDefaultScopeIdForSource()))
            .build();
    }

}
