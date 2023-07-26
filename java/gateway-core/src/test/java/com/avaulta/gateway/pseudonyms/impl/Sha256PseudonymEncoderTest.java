package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class Sha256PseudonymEncoderTest {

    Sha256PseudonymEncoder encoder = new Sha256PseudonymEncoder();


    @ParameterizedTest
    @ValueSource(strings = {
        // examples taken from https://github.com/Worklytics/psoxy/blob/b483e3788d5457398d55cad7934de959b74c7900/java/core/src/test/java/co/worklytics/psoxy/storage/impl/BulkDataSanitizerImplTest.java#L228-L239
        "SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM",
        "mfsaNYuCX__xvnRz4gJp_t0zrDTC5DkuCJvMkubugsI",
        ".ZdDGUuOMK.Oy7_PJ3pf9SYX12.3tKPdLHfYbjVGcGk",
        ".fs1T64Micz8SkbILrABgEv4kSg.tFhvhP35HGSLdOo"
    })
    void canBeDecoded(String encoded) {
        assertTrue(encoder.canBeDecoded(encoded));
    }


    @ParameterizedTest
    @ValueSource(strings = {
        "asdfasdf",
        "1343287afdaskdljf4324sasdfa",
    })
    void cannotBeDecoded(String encoded) {
        assertFalse(encoder.canBeDecoded(encoded));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "asdfasdf",
        "1343287afdaskdljf4324sasdfa",
        "asdf1234234",
        "alice@acme.com"
    })
    void roundtrip(String identifier) {
        Sha256DeterministicTokenizationStrategy sha256DeterministicTokenizationStrategy =
            new Sha256DeterministicTokenizationStrategy("salt");

        Pseudonym pseudonym = Pseudonym.builder()
            .hash(sha256DeterministicTokenizationStrategy.getToken(identifier, Function.identity()))
            .build();

        String encoded = encoder.encode(pseudonym);

        assertTrue(encoder.canBeDecoded(encoded));
        assertEquals(encoder.base64Encode(pseudonym.getHash()), // arguably too much of a tautology
            new String(encoder.decode(encoded).getHash()));

    }
}
