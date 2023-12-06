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
        "test@acme.com,t~dwNMQWOwpMUYHAXHO.xJer1tqMnpdYSAqMzw15OEfTU@acme.com",
        "blah,t~i33xQ9kccW7PpfwXMAIva0IbBc7e6P1SsfxlqWAwrVI",
        "blah2,t~QkKC.YJJeRDR_4K0tG0iX0gAQ.BY8h_Fq2BsPtBiH_I"
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
        "t~i33xQ9kccW7PpfwXMAIva0IbBc7e6P1SsfxlqWAwrV_@acme.com",
        "t~i33xQ9kccW7PpfwXMAIva0IbBc7e6P1SsfxlqWAwrV_",
        "t~i33xQ9kccW7PpfwXMAIva0IbBc7e6P.SsfxlqWAwrV_@acme.com",
        "t~i33xQ9kccW7PpfwXMAIva0IbBc7e6P.SsfxlqWAwrV_",
    })
    @ParameterizedTest
    public void canDecode(String s) {
        assertTrue(legacyPseudonymTokenEncoder.canBeDecoded(s));
    }

    @ValueSource(strings = {
        "t~asdf@acme.com",
        "p~i33xQ9kccW7PpfwXMAIva0IbBc7e6P1SsfxlqWAwrVI@acme.com",
        "t~asdfas",
        "p~i33xQ9kccW7PpfwXMAIva0IbBc7e6P1SsfxlqWAwrVI"
    })
    @ParameterizedTest
    public void cantDecode(String s) {
        assertFalse(legacyPseudonymTokenEncoder.canBeDecoded(s));
    }

}
