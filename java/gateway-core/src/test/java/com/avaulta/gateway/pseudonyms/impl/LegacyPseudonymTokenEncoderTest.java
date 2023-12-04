package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LegacyPseudonymTokenEncoderTest {

    LegacyPseudonymTokenEncoder legacyPseudonymTokenEncoder = new LegacyPseudonymTokenEncoder();

    // test a few cases, including some '.' in output so interesting
    @CsvSource({
        "test@acme.com,l~dwNMQWOwpMUYHAXHO.xJer1tqMnpdYSAqMzw15OEfTU@acme.com",
        "blah,l~i33xQ9kccW7PpfwXMAIva0IbBc7e6P1SsfxlqWAwrVI",
        "blah2,l~QkKC.YJJeRDR_4K0tG0iX0gAQ.BY8h_Fq2BsPtBiH_I"
    })
    @ParameterizedTest
    public void testEncode(String s, String encoded) {
        Pseudonym pseudonym = Pseudonym.builder()
            .hash(DigestUtils.sha256(s))
            .domain(s.contains("@") ? s.substring(s.indexOf("@") + 1) : null)
            .build();

        String result = legacyPseudonymTokenEncoder.encode(pseudonym);
        assertEquals(encoded, result);

        assertTrue(legacyPseudonymTokenEncoder.canBeDecoded(result));

        Pseudonym decoded = legacyPseudonymTokenEncoder.decode(result);

        assertEquals(new String(pseudonym.getHash()), new String(decoded.getHash()));
    }

    @ValueSource(strings = {
        "l~blah@acme.com",
        "l~blah"
    })
    @ParameterizedTest
    public void canDecode(String s) {
        assertTrue(legacyPseudonymTokenEncoder.canBeDecoded(s));
    }

    @ValueSource(strings = {
        "t~blah@acme.com",
        "p~blah@acme.com",
        "t~asdfas",
        "p~asdfas"
    })
    @ParameterizedTest
    public void cantDecode(String s) {
        assertFalse(legacyPseudonymTokenEncoder.canBeDecoded(s));
    }

}
