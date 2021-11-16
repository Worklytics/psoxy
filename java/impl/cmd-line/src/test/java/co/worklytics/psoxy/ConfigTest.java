package co.worklytics.psoxy;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    public void validate_secretReference() {

        Config.SecretReference secretReference = new Config.SecretReference();
        secretReference.service = Config.SecretService.GCP;
        secretReference.identifier = "projects/example-project/secrets/some-secret/versions/1";

        Config.SecretReference.validate(secretReference);


        secretReference.identifier = "projects/not-valid/";

        assertThrows(IllegalArgumentException.class,
            () -> Config.SecretReference.validate(secretReference));


        secretReference.service = Config.SecretService.AWS;
        secretReference.identifier = "projects/example-project/secrets/some-secret/versions/1";

        assertThrows(NotImplementedException.class,
            () -> Config.SecretReference.validate(secretReference));
    }

}
