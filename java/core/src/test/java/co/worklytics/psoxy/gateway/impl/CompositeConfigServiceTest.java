package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompositeConfigServiceTest {

    ConfigService test;

    enum Properties implements ConfigService.ConfigProperty {
        DEFINED_IN_A,
        DEFINED_IN_B,
        DEFINED_IN_BOTH,
        DEFINED_IN_NEITHER,
        ;
    }

    @BeforeEach
    void setup() {
        ConfigService a = mock(ConfigService.class);
        ConfigService b = mock(ConfigService.class);
        when(a.getConfigPropertyAsOptional(eq(Properties.DEFINED_IN_A)))
            .thenReturn(Optional.of("defined-in-a"));

        when(b.getConfigPropertyAsOptional(eq(Properties.DEFINED_IN_B)))
            .thenReturn(Optional.of("defined-in-b"));

        when(a.getConfigPropertyAsOptional(eq(Properties.DEFINED_IN_BOTH)))
            .thenReturn(Optional.of("defined-in-both-a"));
        when(b.getConfigPropertyAsOptional(eq(Properties.DEFINED_IN_BOTH)))
            .thenReturn(Optional.of("defined-in-both-b"));

        when(a.getConfigPropertyAsOptional(eq(Properties.DEFINED_IN_NEITHER)))
            .thenReturn(Optional.empty());
        when(b.getConfigPropertyAsOptional(eq(Properties.DEFINED_IN_NEITHER)))
            .thenReturn(Optional.empty());

        test = CompositeConfigService.builder()
            .preferred(a)
            .fallback(b)
            .build();
    }

    @Test
    void getConfigPropertyOrError() {

        assertEquals("defined-in-a",
            test.getConfigPropertyOrError(Properties.DEFINED_IN_A));
        assertEquals("defined-in-b",
            test.getConfigPropertyOrError(Properties.DEFINED_IN_B));
        assertEquals("defined-in-a",
            test.getConfigPropertyOrError(Properties.DEFINED_IN_A));

        //verify a is preferred
        assertEquals("defined-in-both-a",
            test.getConfigPropertyOrError(Properties.DEFINED_IN_BOTH));

        assertThrows(Error.class,
            () -> test.getConfigPropertyOrError(Properties.DEFINED_IN_NEITHER));
    }

    @Test
    void getConfigPropertyAsOptional() {

        assertEquals("defined-in-a",
            test.getConfigPropertyAsOptional(Properties.DEFINED_IN_A).get());
        assertEquals("defined-in-b",
            test.getConfigPropertyAsOptional(Properties.DEFINED_IN_B).get());
        assertEquals("defined-in-a",
            test.getConfigPropertyAsOptional(Properties.DEFINED_IN_A).get());

        //verify a is preferred
        assertEquals("defined-in-both-a",
            test.getConfigPropertyAsOptional(Properties.DEFINED_IN_BOTH).get());

        assertFalse(test.getConfigPropertyAsOptional(Properties.DEFINED_IN_NEITHER).isPresent());
    }
}
