package com.avaulta.gateway.tokens.impl;

import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Sha256DeterministicTokenizationStrategyTest {
    DeterministicTokenizationStrategy deterministicTokenizationStrategy;

    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();

    @SneakyThrows
    @BeforeEach
    void setUp() {
        deterministicTokenizationStrategy = new Sha256DeterministicTokenizationStrategy("salt");
    }

    @Test
    void pseudonym_sizes() {

        //test a bunch of a values at random
        int randomSamples = 100;

        IntStream.range(0, randomSamples).forEach(i -> {
            //use a random UUID, to give original of typical length
            String original = UUID.randomUUID().toString();
            byte[] pseudonym = deterministicTokenizationStrategy.getToken(original, Function.identity());

            //43 bytes when base64-encoded without padding
            assertEquals(43, new String(encoder.encode(pseudonym)).length());

            //32 bytes unencoded
            assertEquals(deterministicTokenizationStrategy.getTokenLength(), pseudonym.length);
        });
    }
}
