package com.avaulta.gateway.tokens.impl;

import com.avaulta.gateway.pseudonyms.impl.TestUtils;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class AESGCMReversibleTokenizationStrategyTest {

    ReversibleTokenizationStrategy pseudonymizationStrategy;
    DeterministicTokenizationStrategy deterministicTokenizationStrategy;

    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();

    @SneakyThrows
    @BeforeEach
    void setUp() {
        deterministicTokenizationStrategy = new Sha256DeterministicTokenizationStrategy("salt");
        pseudonymizationStrategy =
            AESReversibleTokenizationStrategy.builder()
                .cipherSuite(AESReversibleTokenizationStrategy.GCM)
                .deterministicTokenizationStrategy(deterministicTokenizationStrategy)
                .key(TestUtils.testKey())
                .build();
    }
    @Test
    void reverse() {
        //given 'secret' and 'salt' the same, should be able to decrypt
        // (eg, our key-generation isn't random and nothing has any randomized state persisted
        //  somehow between tests)

       // String s = new String(encoder.encode(pseudonymizationStrategy.getKeyedPseudonym("blah",Function.identity())));

        assertEquals("blah",
            pseudonymizationStrategy.getOriginalDatum(decoder.decode("nVPSMYD7ZO_ptGIMJ65TAFo5_vVVQQ2af5Bfg7bW0JpFszshi2nfr3BovVcPFYct2qRdcA")));
    }
    @Test
    void keyedPseudonym_sizes() {
        Random random = new Random();
        IntStream.generate(() -> random.nextInt(1000000000)).limit(100).forEach(i -> {
            String pseudonym = new String(encoder.encode(pseudonymizationStrategy.getReversibleToken("blah" + i, Function.identity())));
            assertTrue(
                //q: why variable length for constant length input?
                // seem to range from 79 --> 82 bytes
                pseudonym.length() < Pseudonym.HASH_SIZE_BYTES + 52 //hash + cipher
            );
        });
    }

}
