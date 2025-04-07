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

    default Pseudonymizer.ConfigurationOptions buildOptions(ConfigService config) {
        Pseudonymizer.ConfigurationOptions.ConfigurationOptionsBuilder builder = Pseudonymizer.ConfigurationOptions.builder();

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
