package co.worklytics.psoxy.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import co.worklytics.psoxy.ControlHeader;

class TargetOverrideRequestResolverTest {

    TargetOverrideRequestResolver resolver;
    HttpEventRequest request;

    @BeforeEach
    void setUp() {
        resolver = new TargetOverrideRequestResolver();
        request = mock(HttpEventRequest.class);
        when(request.getPath()).thenReturn("/actual/path");
        when(request.getQuery()).thenReturn(Optional.of("a=1"));
        when(request.getHeader(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void noHeaders_returnsSameRequest() {
        HttpEventRequest result = resolver.applyOverrides(request);
        assertSame(request, result);
        assertEquals("/actual/path", result.getPath());
        assertEquals(Optional.of("a=1"), result.getQuery());
    }

    @Test
    void targetPath_overridesPath_keepsQuery() {
        when(request.getHeader(ControlHeader.TARGET_PATH.getHttpHeader()))
            .thenReturn(Optional.of("/v2/users/%7Bid%7D"));

        HttpEventRequest result = resolver.applyOverrides(request);

        assertNotSame(request, result);
        assertEquals("/v2/users/%7Bid%7D", result.getPath());
        assertEquals(Optional.of("a=1"), result.getQuery());
    }

    @Test
    void targetQuery_overridesQuery_keepsPath() {
        when(request.getHeader(ControlHeader.TARGET_QUERY.getHttpHeader()))
            .thenReturn(Optional.of("page=2&size=10"));

        HttpEventRequest result = resolver.applyOverrides(request);

        assertEquals("/actual/path", result.getPath());
        assertEquals(Optional.of("page=2&size=10"), result.getQuery());
    }

    @Test
    void bothHeaders_overridePathAndQuery() {
        when(request.getHeader(ControlHeader.TARGET_PATH.getHttpHeader()))
            .thenReturn(Optional.of("/meetings"));
        when(request.getHeader(ControlHeader.TARGET_QUERY.getHttpHeader()))
            .thenReturn(Optional.of("type=scheduled"));

        HttpEventRequest result = resolver.applyOverrides(request);

        assertEquals("/meetings", result.getPath());
        assertEquals(Optional.of("type=scheduled"), result.getQuery());
    }

    @Test
    void targetQuery_empty_clearsQuery() {
        when(request.getHeader(ControlHeader.TARGET_QUERY.getHttpHeader()))
            .thenReturn(Optional.of(""));

        HttpEventRequest result = resolver.applyOverrides(request);

        assertEquals(Optional.of(""), result.getQuery());
    }

    @Test
    void targetQuery_stripsLeadingQuestionMark() {
        when(request.getHeader(ControlHeader.TARGET_QUERY.getHttpHeader()))
            .thenReturn(Optional.of("?foo=bar"));

        HttpEventRequest result = resolver.applyOverrides(request);

        assertEquals(Optional.of("foo=bar"), result.getQuery());
    }

    @Test
    void targetPath_trimsWhitespace() {
        when(request.getHeader(ControlHeader.TARGET_PATH.getHttpHeader()))
            .thenReturn(Optional.of("  /trimmed  "));

        HttpEventRequest result = resolver.applyOverrides(request);

        assertEquals("/trimmed", result.getPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-slash", "relative/path", ""})
    void targetPath_rejectsMissingLeadingSlashOrEmpty(String path) {
        when(request.getHeader(ControlHeader.TARGET_PATH.getHttpHeader()))
            .thenReturn(Optional.of(path));

        assertThrows(IllegalArgumentException.class, () -> resolver.applyOverrides(request));
    }

    @Test
    void targetPath_rejectsCrLf() {
        when(request.getHeader(ControlHeader.TARGET_PATH.getHttpHeader()))
            .thenReturn(Optional.of("/evil\r\nX-Injected: true"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> resolver.applyOverrides(request));
        assertTrue(e.getMessage().contains("CR/LF"));
    }

    @Test
    void targetPath_rejectsNonAscii() {
        when(request.getHeader(ControlHeader.TARGET_PATH.getHttpHeader()))
            .thenReturn(Optional.of("/café"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> resolver.applyOverrides(request));
        assertTrue(e.getMessage().contains("printable ASCII"));
    }

    @Test
    void targetPath_rejectsOverMaxLength() {
        String tooLong = "/" + "a".repeat(TargetOverrideRequestResolver.MAX_HEADER_VALUE_LENGTH);
        when(request.getHeader(ControlHeader.TARGET_PATH.getHttpHeader()))
            .thenReturn(Optional.of(tooLong));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> resolver.applyOverrides(request));
        assertTrue(e.getMessage().contains("max length"));
    }

    @Test
    void targetQuery_rejectsCrLf() {
        when(request.getHeader(ControlHeader.TARGET_QUERY.getHttpHeader()))
            .thenReturn(Optional.of("a=1\nb=2"));

        assertThrows(IllegalArgumentException.class, () -> resolver.applyOverrides(request));
    }

    @Test
    void wrapper_delegatesOtherMethods() {
        when(request.getHeader(ControlHeader.TARGET_PATH.getHttpHeader()))
            .thenReturn(Optional.of("/override"));
        when(request.getHttpMethod()).thenReturn("GET");
        when(request.getClientIp()).thenReturn(Optional.of("1.2.3.4"));

        HttpEventRequest result = resolver.applyOverrides(request);

        assertEquals("GET", result.getHttpMethod());
        assertEquals(Optional.of("1.2.3.4"), result.getClientIp());
    }
}
