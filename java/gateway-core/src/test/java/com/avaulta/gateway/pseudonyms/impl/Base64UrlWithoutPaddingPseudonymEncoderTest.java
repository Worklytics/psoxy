package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Base64UrlWithoutPaddingPseudonymEncoderTest {


    Base64UrlWithoutPaddingPseudonymEncoder pseudonymEncoder = new Base64UrlWithoutPaddingPseudonymEncoder();

    AESCBCPseudonymizationStrategy pseudonymizationStrategy;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        pseudonymizationStrategy = new AESCBCPseudonymizationStrategy("salt", TestUtils.testKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://api.acme.com/v1/accounts/%s",
        "https://api.acme.com/v1/accounts/%s/calendar",
        "https://api.acme.com/v1/accounts/%s/calendar?param=blah&param2=blah2",
        "https://api.acme.com/v1/accounts?id=%s",
        "https://api.acme.com/v1/accounts/%s?id=%s", //doubles
        "https://api.acme.com/v1/accounts/%s?id=p~12adsfasdfasdf31",  //something else with prefix
        "https://api.acme.com/v1/accounts/p~12adsfasdfasdf31?id=%s", //something else with prefix, before actual value
        "https://api.acme.com/v1/accounts",
        "",
    })
    void reverseAll(String template) {
        String original = "blah";
        String pseudonym =
            pseudonymEncoder.encode(Pseudonym.builder()
                .reversible(pseudonymizationStrategy.getKeyedPseudonym("blah", Function.identity())).build());

        String r = pseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(String.format(template, pseudonym, pseudonym),
            pseudonymizationStrategy);

        assertEquals(String.format(template, original, original), r);
    }
}
