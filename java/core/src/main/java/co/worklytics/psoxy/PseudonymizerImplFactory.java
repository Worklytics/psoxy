package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SecretStore;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import dagger.assisted.AssistedFactory;

import java.util.Optional;

@AssistedFactory
public interface PseudonymizerImplFactory {

    PseudonymizerImpl create(Pseudonymizer.ConfigurationOptions configurationOptions);

    default Pseudonymizer.ConfigurationOptions buildOptions(ConfigService config,
                                                            SecretStore secretStore) {
        Pseudonymizer.ConfigurationOptions.ConfigurationOptionsBuilder builder = Pseudonymizer.ConfigurationOptions.builder()
            .pseudonymizationSalt(secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                .orElseThrow(() -> new Error("Must configure value for SALT to generate pseudonyms")));

        Optional<PseudonymImplementation> pseudonymImplementation = config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYM_IMPLEMENTATION)
            .map(PseudonymImplementation::parseConfigPropertyValue);

        pseudonymImplementation
            .ifPresent(builder::pseudonymImplementation);

        pseudonymImplementation.ifPresent(impl -> {
            if (impl == PseudonymImplementation.LEGACY) {
                throw new IllegalArgumentException("LEGACY pseudonymization is no longer supported with v0.5+");
            }
        });

        config.getConfigPropertyAsOptional(ProxyConfigProperty.EMAIL_CANONICALIZATION)
            .map(EmailCanonicalization::parseConfigPropertyValue)
            .ifPresent(builder::emailCanonicalization);


        return builder.build();
    }
}
