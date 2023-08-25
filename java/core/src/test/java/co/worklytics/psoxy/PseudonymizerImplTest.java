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
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Base64;

import static co.worklytics.test.TestModules.withMockEncryptionKey;
import static org.junit.jupiter.api.Assertions.*;

class PseudonymizerImplTest {


    static final String ALICE_CANONICAL = "alice@worklytics.co";


    @Inject
    ConfigService config;
    @Inject
    PseudonymizerImplFactory pseudonymizerImplFactory;


    Pseudonymizer pseudonymizer;


    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        MockModules.ForConfigService.class,
    })
    public interface Container {
        void inject(PseudonymizerImplTest test);
    }

    @BeforeEach
    public void setup() {
        PseudonymizerImplTest.Container container = DaggerPseudonymizerImplTest_Container.create();
        container.inject(this);

        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
            .pseudonymizationSalt("an irrelevant per org secret")
            .defaultScopeId("scope")
            .pseudonymImplementation(PseudonymImplementation.DEFAULT)
            .build());

        withMockEncryptionKey(config);
    }

    @ValueSource(strings = {
        "alice@worklytics.co",
        "Alice Example <alice@worklytics.co>",
        "\"Alice Example\" <alice@worklytics.co>",
        "Alice.Example@worklytics.co"
    })
    @ParameterizedTest
    void emailDomains(String mailHeaderValue) {
        assertEquals("worklytics.co", pseudonymizer.pseudonymize(mailHeaderValue).getDomain());
    }

    @ValueSource(strings = {
        ALICE_CANONICAL,
        "Alice Example <alice@worklytics.co>",
        "\"Alice Different Last name\" <alice@worklytics.co>",
        "Alice@worklytics.co",
        "AlIcE@worklytics.co",
    })
    @ParameterizedTest
    void emailCanonicalEquivalents(String mailHeaderValue) {
        PseudonymizedIdentity canonicalExample = pseudonymizer.pseudonymize(ALICE_CANONICAL);

        assertEquals(canonicalExample.getHash(),
            pseudonymizer.pseudonymize(mailHeaderValue).getHash());
    }

    @ValueSource(strings = {
        "bob@worklytics.co",
        "Alice Example <alice2@worklytics.co>",
        "\"Alice Example\" <alice-a@worklytics.co>",
        "Alice@somewhere-else.co",
        "AlIcE.Other@worklytics.co",
    })
    @ParameterizedTest
    void emailCanonicalDistinct(String mailHeaderValue) {
        PseudonymizedIdentity  canonicalExample = pseudonymizer.pseudonymize(ALICE_CANONICAL);

        assertNotEquals(canonicalExample.getHash(),
            pseudonymizer.pseudonymize(mailHeaderValue).getHash());
    }


    @Test
    void hashMatchesLegacy() {
        //value taken from legacy app

        final String CANONICAL = "original";

        //value taken from legacy app
        final String identityHash = "xqUOU_DGuUAw4ErZIFL4pGx3bZDrFfLU6jQC4ClhrJI";

        assertEquals(identityHash,
            pseudonymizer.pseudonymize(CANONICAL).getHash());
    }


    @Test
    void hashesMatchRegardlessOfReversible() {
        final String CANONICAL = "original";

        assertEquals(
            pseudonymizer.pseudonymize(CANONICAL, Transform.Pseudonymize.builder().includeReversible(false).build()).getHash(),
            pseudonymizer.pseudonymize(CANONICAL, Transform.Pseudonymize.builder().includeReversible(true).build()).getHash());
    }

    @Test
    void encoderRoundtripForReversibles() {
        final String CANONICAL = "original";

        UrlSafeTokenPseudonymEncoder urlSafeTokenPseudonymEncoder = new UrlSafeTokenPseudonymEncoder();
        PseudonymizedIdentity pseudonym = pseudonymizer.pseudonymize(CANONICAL, Transform.Pseudonymize.builder().includeReversible(false).build());
        PseudonymizedIdentity reversiblePseudonym = pseudonymizer.pseudonymize(CANONICAL, Transform.Pseudonymize.builder().includeReversible(true).build());

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();


        assertEquals(
                pseudonym.getHash(),
            encoder.encodeToString(reversiblePseudonym.asPseudonym().getHash()));

        Pseudonym decoded = urlSafeTokenPseudonymEncoder.decode(reversiblePseudonym.getReversible());

        assertEquals(
                encoder.encodeToString(pseudonym.asPseudonym().getHash()),
                encoder.encodeToString(decoded.getHash()));
    }

}
