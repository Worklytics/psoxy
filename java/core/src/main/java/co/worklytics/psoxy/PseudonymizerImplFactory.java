package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface PseudonymizerImplFactory {

    PseudonymizerImpl create(Pseudonymizer.ConfigurationOptions configurationOptions);

    default Pseudonymizer.ConfigurationOptions buildOptions(ConfigService config, String defaultScopeIdForSource) {
        Pseudonymizer.ConfigurationOptions.ConfigurationOptionsBuilder builder = Pseudonymizer.ConfigurationOptions.builder()
            .pseudonymizationSalt(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                .orElseThrow(() -> new Error("Must configure value for SALT to generate pseudonyms")))
            .defaultScopeId(config.getConfigPropertyAsOptional(ProxyConfigProperty.IDENTIFIER_SCOPE_ID)
                .orElse(defaultScopeIdForSource));

        config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYM_IMPLEMENTATION)
            .map(PseudonymImplementation::parseConfigPropertyValue)
            .ifPresent(builder::pseudonymImplementation);


        return builder.build();
    }
}
