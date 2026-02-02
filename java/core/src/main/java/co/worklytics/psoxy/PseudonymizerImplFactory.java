package co.worklytics.psoxy;

import java.util.Optional;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface PseudonymizerImplFactory {

    PseudonymizerImpl create(Pseudonymizer.ConfigurationOptions configurationOptions);

    // explicitly checking for LEGACY to warn user, so must suppress deprecation warning
    @SuppressWarnings("deprecation")
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

        config.getConfigPropertyAsOptional(ProxyConfigProperty.EMAIL_DOMAIN_HANDLING)
            .map(EmailDomainHandling::parseConfigPropertyValue)
            .ifPresent(builder::emailDomainHandling);

        return builder.build();
    }
}
