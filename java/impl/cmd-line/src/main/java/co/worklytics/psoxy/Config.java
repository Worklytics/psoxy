package co.worklytics.psoxy;

import com.google.common.base.Preconditions;
import lombok.*;
import org.apache.commons.lang3.NotImplementedException;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor //for Jackson
public class Config {

    String defaultScopeId;

    @Builder.Default
    Set<String> columnsToPseudonymize = new HashSet<>();

    @Builder.Default
    Set<String> columnsToRedact = new HashSet<>();

    /**
     * salt to use when generating pseudonyms
     */
    String pseudonymizationSalt;

    /**
     * identifier of secret containing value to use salt pseudonyms.
     */
    SecretReference pseudonymizationSaltSecret;


    @AllArgsConstructor(staticName = "of")
    @Getter
    @NoArgsConstructor //for Jackson
    public static class SecretReference {

        SecretService service;

        String identifier;

        static void validate(SecretReference secretReference) {
            Preconditions.checkNotNull(secretReference.service, "SecretReference.service cannot be null");

            if (secretReference.service == SecretService.GCP) {
                if (!secretReference.service.getIdentifierPatternAsPattern().matcher(secretReference.identifier).matches()) {
                    throw new IllegalArgumentException("secret identifier "
                        + secretReference.identifier
                        + " does not match expected pattern " + secretReference.getService().getIdentifierPattern());
                }
            } else {
                throw new NotImplementedException("Service " + secretReference.service + " not yet implemented");
            }
        }
    }



    @RequiredArgsConstructor
    enum SecretService {
        AWS_PARAMETER_STORE("not implemented"),
        AWS_SECRET_MANAGER("not implemented"),
        AZURE("not implemented"),
        GCP("^projects/.*?/secrets/.*/versions/.*$");

        @Getter
        @NonNull
        final String identifierPattern;

        Pattern getIdentifierPatternAsPattern() {
            return Pattern.compile(this.identifierPattern);
        }

    }

    static void validate(Config config) {
        if (config.getPseudonymizationSaltSecret() == null) {
            Preconditions.checkNotNull(config.pseudonymizationSalt, "Must provide salt or reference to secret which contains it");
        } else {
            SecretReference.validate(config.getPseudonymizationSaltSecret());
        }
    }
}
