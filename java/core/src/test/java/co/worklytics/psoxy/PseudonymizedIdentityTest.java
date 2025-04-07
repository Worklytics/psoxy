package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.BulkModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.transforms.Transform;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Base64;

import static co.worklytics.test.TestModules.withMockEncryptionKey;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class PseudonymizedIdentityTest {

    @Singleton
    @Component(modules = {
            PsoxyModule.class,
            MockModules.ForConfigService.class,
            MockModules.ForSecretStore.class,
            MockModules.ForRules.class,
    })
    public interface Container {
        void inject(PseudonymizedIdentityTest test);
    }

    @BeforeEach
    public void setup() {
        PseudonymizedIdentityTest.Container container = DaggerPseudonymizedIdentityTest_Container.create();
        container.inject(this);

        withMockEncryptionKey(secretStore);
        when(config.getConfigPropertyOrError(eq(ProxyConfigProperty.SOURCE)))
            .thenReturn("gmail");
    }


    @Inject ConfigService config;
    @Inject SecretStore secretStore;
    @Inject Pseudonymizer pseudonymizer;
    @Inject PseudonymizerImplFactory pseudonymizerImplFactory;

    @Test
    void asPseudonym() {
        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymImplementation(PseudonymImplementation.DEFAULT)
                .build());

        PseudonymizedIdentity pseudonymizedIdentity = pseudonymizer.pseudonymize("alice@acme.com");

        Pseudonym pseudonym = pseudonymizedIdentity.asPseudonym();

        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(pseudonym.getHash()),
                pseudonymizedIdentity.getHash());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "    "
    })
    void asPseudonym_should_return_null_if_null_or_empty(String identifier) {
        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymImplementation(PseudonymImplementation.DEFAULT)
                .build());

        PseudonymizedIdentity pseudonymizedIdentity = pseudonymizer.pseudonymize(identifier);

        assertNull(pseudonymizedIdentity);
    }

    @Test
    void asPseudonym_reversible() {
        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymImplementation(PseudonymImplementation.DEFAULT)
                .build());

        PseudonymizedIdentity pseudonymizedIdentity
                = pseudonymizer.pseudonymize("alice@acme.com", Transform.Pseudonymize.builder()
                .includeReversible(true)
                .build());

        PseudonymizedIdentity notReversible = pseudonymizer.pseudonymize("alice@acme.com", Transform.Pseudonymize.builder()
                .includeReversible(false)
                .build());

        Pseudonym pseudonym = pseudonymizedIdentity.asPseudonym();

        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(pseudonym.getHash()),
                pseudonymizedIdentity.getHash());

        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(pseudonym.getHash()),
                notReversible.getHash());

        // hash on the reversible IS equal to the hash for the default pseudonyms
        UrlSafeTokenPseudonymEncoder urlSafeTokenPseudonymEncoder = new UrlSafeTokenPseudonymEncoder();
        assertNotEquals(pseudonymizedIdentity.getHash(),
            urlSafeTokenPseudonymEncoder.decode(pseudonymizedIdentity.getReversible()).getHash());
    }

}
