package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.DeterministicPseudonymStrategy;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Sha256DeterministicPseudonymStrategyTest{
    DeterministicPseudonymStrategy deterministicPseudonymStrategy;

    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();

    @SneakyThrows
    @BeforeEach
    void setUp() {
        deterministicPseudonymStrategy = new Sha256DeterministicPseudonymStrategy("salt");
    }

    @Test
    void pseudonym_sizes() {

        //test a bunch of a values at random
        int randomSamples = 100;

        IntStream.range(0, randomSamples).forEach(i -> {
            //use a random UUID, to give original of typical length
            String original = UUID.randomUUID().toString();
            byte[] pseudonym = deterministicPseudonymStrategy.getPseudonym(original, Function.identity());

            //43 bytes when base64-encoded without padding
            assertEquals(43, new String(encoder.encode(pseudonym)).length());

            //32 bytes unencoded
            assertEquals(deterministicPseudonymStrategy.getPseudonymLength(), pseudonym.length);
        });
    }
}
