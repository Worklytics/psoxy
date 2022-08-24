package co.worklytics.test;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.inject.Singleton;

import java.security.spec.KeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TestModules {


    @Module
    public interface ForFixedClock {

        @Provides
        @Singleton
        static Clock fixedClock() {
            return Clock.fixed(Instant.parse("2021-12-15T00:00:00Z"), ZoneId.of("UTC"));
        }
    }

    //provide deterministic, fixed UUID from `Provider<UUID>` instead of random
    @Module
    public interface ForFixedUUID {

        @Provides
        static UUID uuid() {
            return UUID.fromString("886cd2d1-2a1d-43e9-91d4-6a2b166dff9e");
        }
    }


    //TODO: probably better to just inject filled AESReversibleEncryptionStrategy, rather than
    // mocking ConfigService sufficiently such that regular provider can build one
    @SneakyThrows
    public static void withMockEncryptionKey(ConfigService config) {

        //SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        //KeySpec spec = new PBEKeySpec("secret".toCharArray(), "salt".getBytes(), 65536, 256);
        //SecretKey tmp = factory.generateSecret(spec);


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

        //String key = new String(Base64.getEncoder().encode(tmp.getEncoded()));

        when(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_ENCRYPTION_KEY))
            .thenReturn(Optional.of("secret"));
        when(config.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT))
            .thenReturn("salt");
    }
}
