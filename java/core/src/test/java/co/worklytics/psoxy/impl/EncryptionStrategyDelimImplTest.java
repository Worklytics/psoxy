package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static co.worklytics.test.TestModules.withMockEncryptionKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class EncryptionStrategyDelimImplTest {

    EncryptionStrategyDelimImpl encryptionStrategy;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        encryptionStrategy = new EncryptionStrategyDelimImpl();

        encryptionStrategy.config = mock(ConfigService.class);
        withMockEncryptionKey(encryptionStrategy.config);
    }


    @Test
    void roundtrip() {

        String encrypted = encryptionStrategy.encrypt("blah");
        assertNotEquals("blah", encrypted);

        //something else shouldn't match
        String encrypted2 = encryptionStrategy.encrypt("blah2");
        assertNotEquals(encrypted2, encrypted);

        String decrypted = encryptionStrategy.decrypt(encrypted);
        assertEquals("blah", decrypted);
    }

    @Test
    void decrypt() {
        //given 'secret' and 'salt' the same, should be able to decrypt
        // (eg, our key-generation isn't random and nothing has any randomized state persisted
        //  somehow between tests)

        assertEquals("blah",
            encryptionStrategy.decrypt("NHXWS5CZDysDs3ETExXiMZxM2DfffirkjgmA64R9hCc:px2zWz7DreFvh8fEg1GkGA"));
    }
}
