package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.test.MockModules;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.transforms.Transform;
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
    void asPseudonym_should_return_null_if_null_or_empty(String identifier) {
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

        // hash on the reversible IS equal to the hash for the default pseudonyms
        UrlSafeTokenPseudonymEncoder urlSafeTokenPseudonymEncoder = new UrlSafeTokenPseudonymEncoder();
        assertNotEquals(pseudonymizedIdentity.getHash(),
            urlSafeTokenPseudonymEncoder.decode(pseudonymizedIdentity.getReversible()).getHash());
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

        assertEquals("BlFx65qHrkRrhMsuq7lg4bCpwsbXgpLhVZnZ6VBMqoY",
            pseudonymizedIdentity.getHash());

        PseudonymizedIdentity notReversible =
            pseudonymizer.pseudonymize("alice@acme.com", Transform.Pseudonymize.builder()
                .includeReversible(false)
                .build());

        // round trip from legacy should now be working OK, even with reversible
        Pseudonym pseudonym = pseudonymizedIdentity.fromLegacy();

        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(pseudonym.getHash()),
                pseudonymizedIdentity.getHash());

        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(pseudonym.getHash()),
                notReversible.getHash());

        // hash on the reversible is NOT equal to the hash for the legacy pseudonyms
        UrlSafeTokenPseudonymEncoder urlSafeTokenPseudonymEncoder = new UrlSafeTokenPseudonymEncoder();
        assertNotEquals(pseudonymizedIdentity.getHash(),
            urlSafeTokenPseudonymEncoder.decode(pseudonymizedIdentity.getReversible()).getHash());
    }
}
