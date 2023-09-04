package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.test.MockModules;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.rules.transforms.Transform;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;
import dagger.Component;
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

class PseudonymizedIdentityTest {

    @Singleton
    @Component(modules = {
            PsoxyModule.class,
            MockModules.ForConfigService.class,
            MockModules.ForRules.class,
    })
    public interface Container {
        void inject(PseudonymizedIdentityTest test);
    }
    @BeforeEach
    public void setup() {
        PseudonymizedIdentityTest.Container container = DaggerPseudonymizedIdentityTest_Container.create();
        container.inject(this);

        withMockEncryptionKey(config);
    }

    @Inject ConfigService config;
    @Inject Pseudonymizer pseudonymizer;
    @Inject PseudonymizerImplFactory pseudonymizerImplFactory;

    @Test
    void asPseudonym() {
        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("an irrelevant per org secret")
                .defaultScopeId("scope")
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
    void asPseudonym_should_return_empty_if_null_or_empty(String identifier) {
        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("an irrelevant per org secret")
                .defaultScopeId("scope")
                .pseudonymImplementation(PseudonymImplementation.DEFAULT)
                .build());

        PseudonymizedIdentity pseudonymizedIdentity = pseudonymizer.pseudonymize(identifier);

        assertNull(pseudonymizedIdentity);
    }

    @Test
    void asPseudonym_reversible() {
        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("an irrelevant per org secret")
                .defaultScopeId("scope")
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
    }

    @Test
    void asPseudonym_legacy() {
        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("an irrelevant per org secret")
                .defaultScopeId("scope")
                .pseudonymImplementation(PseudonymImplementation.LEGACY)
                .build());

        PseudonymizedIdentity pseudonymizedIdentity = pseudonymizer.pseudonymize("alice@acme.com");

        Pseudonym pseudonym = pseudonymizedIdentity.fromLegacy();

        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(pseudonym.getHash()),
                pseudonymizedIdentity.getHash());
    }

    @Test
    void asPseudonym_reversible_legacy() {
        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("an irrelevant per org secret")
                .defaultScopeId("scope")
                .pseudonymImplementation(PseudonymImplementation.LEGACY)
                .build());

        PseudonymizedIdentity pseudonymizedIdentity
                = pseudonymizer.pseudonymize("alice@acme.com", Transform.Pseudonymize.builder()
                .includeReversible(true)
                .build());

        PseudonymizedIdentity notReversible = pseudonymizer.pseudonymize("alice@acme.com", Transform.Pseudonymize.builder()
                .includeReversible(false)
                .build());

        Pseudonym pseudonym = pseudonymizedIdentity.fromLegacy();

        //pseudonym's hash is NOT equivalent to legacy pseudonymizedIdentity's hash
        assertNotEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(pseudonym.getHash()),
                pseudonymizedIdentity.getHash());

        assertNotEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(pseudonym.getHash()),
                notReversible.getHash());
    }
}