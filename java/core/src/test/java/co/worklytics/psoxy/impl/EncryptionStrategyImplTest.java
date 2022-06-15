package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import java.security.spec.KeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EncryptionStrategyImplTest {

    EncryptionStrategyImpl encryptionStrategy;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        encryptionStrategy = new EncryptionStrategyImpl();

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec("secret".toCharArray(), "salt".getBytes(), 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);


        //32-char (256-bit) encoded key
        // how to fill in prod?
        //    -- expect customers to generate this with Terraform `random_password(length=32)`??
        //    -- just use the password-based approach for the config value? (eg, customers configure
        //       a password, and we use that + the encryption salt to generate the key)
        //    -- proxy instances generate key itself? (requiring that can persist back to secrets,
        //       but enabling rotation without re-run of Terraform)
        //    -- salt key with time-based value or something, mod'd to rotation_time (1 week), and
        //       try X trailing values for decryption??
        //    -- randomly generate salt in proxy, pass around in headers???

        String key = new String(Base64.getEncoder().encode(tmp.getEncoded()));

        encryptionStrategy.config = mock(ConfigService.class);
        when(encryptionStrategy.config.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_ENCRYPTION_KEY))
            .thenReturn(key);
    }


    @Test
    void roundtrip() {

        String encrypted = encryptionStrategy.encrypt("blah");
        assertNotEquals("blah", encrypted);

        //something else shouldn't match
        String encrypted2 = encryptionStrategy.encrypt("blah2");
        assertNotEquals(encrypted2, encrypted);

        //iv vectors should be different
        assertNotEquals(encrypted.split(EncryptionStrategyImpl.IV_DELIMITER)[0],
            encrypted2.split(EncryptionStrategyImpl.IV_DELIMITER)[0]);


        String decrypted = encryptionStrategy.decrypt(encrypted);
        assertEquals("blah", decrypted);
    }

    @Test
    void decrypt() {
        //given 'secret' and 'salt' the same, should be able to decrypt
        // (eg, our key-generation isn't random and nothing has any randomized state persisted
        //  somehow between tests)
        assertEquals("blah",
            encryptionStrategy.decrypt("bEtPK4lKBIt2IHXYqagJfw==:6URnvTx3sRoRlyySsTjSVA=="));
    }
}
