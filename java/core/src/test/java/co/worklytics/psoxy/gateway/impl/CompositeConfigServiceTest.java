package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.WritePropertyRetriesExhaustedException;
import co.worklytics.test.MockModules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        ConfigService a = MockModules.provideMock(ConfigService.class);
        ConfigService b = MockModules.provideMock(ConfigService.class);
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

        assertThrows(NoSuchElementException.class,
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

    @Test
    void putConfigProperty() {
        ConfigService supportsWriting = MockModules.provideMock(ConfigService.class);
        when(supportsWriting.supportsWriting()).thenReturn(true);
        ConfigService doesNotSupportWriting = MockModules.provideMock(ConfigService.class);
        when(doesNotSupportWriting.supportsWriting()).thenReturn(false);

        CompositeConfigService configService = CompositeConfigService.builder()
            .preferred(doesNotSupportWriting)
            .fallback(supportsWriting)
            .build();

        ConfigService.ConfigProperty anyProperty = Properties.DEFINED_IN_NEITHER;
        String anyValue = "any-value";
        configService.putConfigProperty(anyProperty, anyValue);
        verify(doesNotSupportWriting, never()).putConfigProperty(eq(anyProperty), anyString());
        verify(supportsWriting, atMostOnce()).putConfigProperty(eq(anyProperty), eq(anyValue));
    }

    /**
     * These tests mostly cover ConfigService.putConfigProperty(ConfigProperty, String, int), defined in
     * the ConfigService interface. But tested along with the composite to check proper behavior
     * as is the usual case.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void putConfigPropertyWithRetriesFails(int retries) {
        ConfigService supportsWriting = MockModules.provideMock(ConfigService.class);
        when(supportsWriting.supportsWriting()).thenReturn(true);
        doAnswer(invocation -> {
            throw new IOException("write failed");
        }).when(supportsWriting).putConfigProperty(any(), anyString());

        ConfigService doesNotSupportWriting = MockModules.provideMock(ConfigService.class);
        when(doesNotSupportWriting.supportsWriting()).thenReturn(false);

        CompositeConfigService configService = CompositeConfigService.builder()
            .preferred(doesNotSupportWriting)
            .fallback(supportsWriting)
            .build();

        ConfigService.ConfigProperty anyProperty = Properties.DEFINED_IN_NEITHER;
        String anyValue = "any-value";

        assertThrows(WritePropertyRetriesExhaustedException.class, () -> configService.putConfigProperty(anyProperty, anyValue, retries));
        verify(doesNotSupportWriting, never()).putConfigProperty(eq(anyProperty), anyString());
        verify(supportsWriting, times(retries)).putConfigProperty(eq(anyProperty), eq(anyValue));
    }

    @Test
    void putConfigPropertyWithNoRetriesFails() {
        ConfigService supportsWriting = MockModules.provideMock(ConfigService.class);
        when(supportsWriting.supportsWriting()).thenReturn(true);

        ConfigService doesNotSupportWriting = MockModules.provideMock(ConfigService.class);
        when(doesNotSupportWriting.supportsWriting()).thenReturn(false);

        CompositeConfigService configService = CompositeConfigService.builder()
            .preferred(doesNotSupportWriting)
            .fallback(supportsWriting)
            .build();

        ConfigService.ConfigProperty anyProperty = Properties.DEFINED_IN_NEITHER;
        String anyValue = "any-value";

        assertThrows(IllegalArgumentException.class, () -> configService.putConfigProperty(anyProperty, anyValue, 0));
        verify(doesNotSupportWriting, never()).putConfigProperty(eq(anyProperty), anyString());
        verify(supportsWriting, never()).putConfigProperty(eq(anyProperty), eq(anyValue));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void putConfigPropertyWithRetriesWorks(int retries) {
        ConfigService supportsWriting = MockModules.provideMock(ConfigService.class);
        when(supportsWriting.supportsWriting()).thenReturn(true);

        ConfigService doesNotSupportWriting = MockModules.provideMock(ConfigService.class);
        when(doesNotSupportWriting.supportsWriting()).thenReturn(false);

        CompositeConfigService configService = CompositeConfigService.builder()
            .preferred(doesNotSupportWriting)
            .fallback(supportsWriting)
            .build();

        ConfigService.ConfigProperty anyProperty = Properties.DEFINED_IN_NEITHER;
        String anyValue = "any-value";

        try {
            configService.putConfigProperty(anyProperty, anyValue, retries);
            verify(doesNotSupportWriting, never()).putConfigProperty(eq(anyProperty), anyString());
            verify(supportsWriting).putConfigProperty(eq(anyProperty), eq(anyValue));
        } catch (WritePropertyRetriesExhaustedException e) {
           fail("shouldn't throw exception");
        }
    }
}
