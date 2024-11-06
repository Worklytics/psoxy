package co.worklytics.psoxy;

import co.worklytics.test.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        objectMapper = new ObjectMapper(new YAMLFactory());
    }

    @Test
    public void validate_gcp_secretReference() {

        Config.SecretReference secretReference = new Config.SecretReference();
        secretReference.service = Config.SecretService.GCP;
        secretReference.identifier = "projects/example-project/secrets/some-secret/versions/1";

        Config.SecretReference.validate(secretReference);


        secretReference.identifier = "projects/not-valid/";

        assertThrows(IllegalArgumentException.class,
            () -> Config.SecretReference.validate(secretReference));


        secretReference.service = Config.SecretService.AWS_PARAMETER_STORE;
        secretReference.identifier = "projects/example-project/secrets/some-secret/versions/1";

        assertThrows(NotImplementedException.class,
            () -> Config.SecretReference.validate(secretReference));
    }

    @SneakyThrows
    @ValueSource(strings = {"config/with-secret.yaml", "config/explicit-salt.yaml"})
    @ParameterizedTest
    public void validate(String example) {

        String serialized = new String(TestUtils.getData(example));
        Config config = objectMapper.readerFor(Config.class).readValue(serialized);
        Config.validate(config);
    }

    @SneakyThrows
    @Test
    public void columnsToRedact_default () {
        String serialized = new String(TestUtils.getData("config/with-secret.yaml"));
        Config config = objectMapper.readerFor(Config.class).readValue(serialized);
        assertNotNull(config.getColumnsToRedact());
        assertTrue(config.getColumnsToRedact().isEmpty());
    }

    @SneakyThrows
    @Test
    public void columnsToRedact() {
        String serialized = new String(TestUtils.getData("config/columns-to-redact.yaml"));
        Config config = objectMapper.readerFor(Config.class).readValue(serialized);
        assertNotNull(config.getColumnsToRedact());
        assertFalse(config.getColumnsToRedact().isEmpty());
        assertTrue(config.getColumnsToRedact().contains("managerEmail"));
    }

}
