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
import java.util.Base64;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TestModules {


    @Module
    public interface ForConfigService {

        @SneakyThrows
        @Provides
        @Singleton
        static ConfigService configService() {
            ConfigService config = mock(ConfigService.class);
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

            when(config.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_ENCRYPTION_KEY))
                .thenReturn(key);

            when(config.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT))
                .thenReturn("secret");

            return config;
        }
    }
}
