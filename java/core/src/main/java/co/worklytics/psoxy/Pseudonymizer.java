package co.worklytics.psoxy;

import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.rules.transforms.Transform;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.io.Serializable;

public interface Pseudonymizer {

    @With
    @Builder
    @Value
    class ConfigurationOptions implements Serializable {

        private static final long serialVersionUID = 5L;

        /**
         * salt used to generate pseudonyms
         * <p>
         * q: split this out? it's per-customer, not per-source API (although it *could* be the
         * later, if you don't need to match with it)
         */
        String pseudonymizationSalt;

        /**
         * scope to use where logic + rules don't imply a match
         */
        @Deprecated
        String defaultScopeId;

        @Builder.Default
        PseudonymImplementation pseudonymImplementation = PseudonymImplementation.DEFAULT;

    }

    /**
     * @param identifier to pseudonymize
     * @return identifier as a PseudonymizedIdentity
     */
    PseudonymizedIdentity pseudonymize(Object identifier);

    PseudonymizedIdentity pseudonymize(Object identifier, Transform.Pseudonymize transform);

    ConfigurationOptions getOptions();
}
