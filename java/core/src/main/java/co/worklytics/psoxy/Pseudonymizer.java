package co.worklytics.psoxy;

import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.rules.transforms.Transform;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.io.Serial;
import java.io.Serializable;

public interface Pseudonymizer {

    @With
    @Builder
    @Value
    class ConfigurationOptions implements Serializable {

        @Serial
        private static final long serialVersionUID = 6L;

        @Builder.Default
        PseudonymImplementation pseudonymImplementation = PseudonymImplementation.DEFAULT;

        @Builder.Default
        EmailCanonicalization emailCanonicalization = EmailCanonicalization.STRICT;

        @Builder.Default
        EmailDomainHandling emailDomainHandling = EmailDomainHandling.PRESERVE;
    }

    /**
     * @param identifier to pseudonymize
     * @return identifier as a PseudonymizedIdentity
     */
    PseudonymizedIdentity pseudonymize(Object identifier);

    PseudonymizedIdentity pseudonymize(Object identifier, Transform.PseudonymizationTransform transform);

    ConfigurationOptions getOptions();
}
