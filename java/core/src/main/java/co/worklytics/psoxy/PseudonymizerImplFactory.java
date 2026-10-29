package co.worklytics.psoxy;

import java.util.Optional;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import co.worklytics.psoxy.gateway.ProxyConfig;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface PseudonymizerImplFactory {

    PseudonymizerImpl create(Pseudonymizer.ConfigurationOptions configurationOptions);

    // explicitly checking for LEGACY to warn user, so must suppress deprecation warning
    @SuppressWarnings("deprecation")
    default Pseudonymizer.ConfigurationOptions buildOptions(ProxyConfig proxyConfig) {
        Pseudonymizer.ConfigurationOptions.ConfigurationOptionsBuilder builder = Pseudonymizer.ConfigurationOptions.builder();

        Optional<PseudonymImplementation> pseudonymImplementation = proxyConfig.getPseudonymImplementation();

        pseudonymImplementation
            .ifPresent(builder::pseudonymImplementation);

        pseudonymImplementation.ifPresent(impl -> {
            if (impl == PseudonymImplementation.LEGACY) {
                throw new IllegalArgumentException("LEGACY pseudonymization is no longer supported with v0.5+");
            }
        });

        proxyConfig.getEmailCanonicalization()
            .ifPresent(builder::emailCanonicalization);

        proxyConfig.getEmailDomainHandling()
            .ifPresent(builder::emailDomainHandling);

        return builder.build();
    }
}
