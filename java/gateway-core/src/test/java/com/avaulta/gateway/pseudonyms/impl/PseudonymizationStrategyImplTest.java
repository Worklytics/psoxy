package com.avaulta.gateway.pseudonyms.impl;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class PseudonymizationStrategyImplTest {

    PseudonymizationStrategyImpl pseudonymizationStrategy;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        pseudonymizationStrategy = new PseudonymizationStrategyImpl("salt", TestUtils.testKey());
    }


    @Test
    void roundtrip() {

        String pseudonym = pseudonymizationStrategy.getKeyedPseudonym("blah", Function.identity());
        assertNotEquals("blah", pseudonym);

        //something else shouldn't match
        String pseudonym2 = pseudonymizationStrategy.getKeyedPseudonym("blah2", Function.identity());
        assertNotEquals(pseudonym2, pseudonym);

        String decrypted = pseudonymizationStrategy.getIdentifier(pseudonym);
        assertEquals("blah", decrypted);
    }

    @Test
    void reverse() {
        //given 'secret' and 'salt' the same, should be able to decrypt
        // (eg, our key-generation isn't random and nothing has any randomized state persisted
        //  somehow between tests)

        assertEquals("blah",
            pseudonymizationStrategy.getIdentifier("NHXWS5CZDysDs3ETExXiMZxM2DfffirkjgmA64R9hCenHbNbPsOt4W-Hx8SDUaQY"));
    }
}
