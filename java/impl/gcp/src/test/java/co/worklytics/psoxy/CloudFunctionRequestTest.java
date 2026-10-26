package co.worklytics.psoxy;

import com.google.cloud.functions.HttpRequest;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static co.worklytics.psoxy.CloudFunctionRequest.DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudFunctionRequestTest {

    @Test
    void getHeader() {
        HttpRequest nativeRequest = mock(HttpRequest.class);

        when(nativeRequest.getHeaders()).thenReturn(Map.of(
            "foo", Lists.newArrayList("bar"),
            "UC-Foo", Lists.newArrayList("barUC")
        ));

        CloudFunctionRequest request = CloudFunctionRequest.of(nativeRequest, DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET);

        assertEquals("bar", request.getHeader("foo").get());
        assertEquals("bar", request.getHeader("Foo").get());
        assertEquals("bar", request.getHeader("FOO").get());
        assertEquals("barUC", request.getHeader("UC-Foo").get());
        assertEquals("barUC", request.getHeader("uc-foo").get());
        assertEquals("barUC", request.getHeader("UC-FOO").get());
        assertTrue(request.getHeader("not-there").isEmpty());
    }

    @Test
    void isHttps_usesCaseInsensitiveForwardedProtoHeader() {
        HttpRequest nativeRequest = mock(HttpRequest.class);

        when(nativeRequest.getHeaders()).thenReturn(Map.of(
            "x-forwarded-proto", Lists.newArrayList("https")
        ));

        CloudFunctionRequest request = CloudFunctionRequest.of(nativeRequest, DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET);

        assertTrue(request.isHttps().get());
    }

    @Test
    void getClientIp_positionMinusOne_usesLastHop() {
        CloudFunctionRequest request = requestWithForwardedFor(
            "10.0.0.5, 203.0.113.10",
            DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET);

        assertEquals("203.0.113.10", request.getClientIp().get());
    }

    @Test
    void getClientIp_positionMinusOne_singleHopIsThatAddress() {
        CloudFunctionRequest request = requestWithForwardedFor(
            "203.0.113.10",
            DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET);

        assertEquals("203.0.113.10", request.getClientIp().get());
    }

    @Test
    void getClientIp_positionMinusTwo_usesSecondToLastHop() {
        CloudFunctionRequest request = requestWithForwardedFor(
            "203.0.113.10, 198.51.100.7",
            -2);

        assertEquals("203.0.113.10", request.getClientIp().get());
    }

    @Test
    void getClientIp_positionMinusTwo_ignoresPrefixBeforeTheSelectedHop() {
        CloudFunctionRequest request = requestWithForwardedFor(
            "10.0.0.5, 198.51.100.7, 203.0.113.10",
            -2);

        assertEquals("198.51.100.7", request.getClientIp().get());
    }

    @Test
    void getClientIp_positionBeyondHeader_isEmpty() {
        CloudFunctionRequest request = requestWithForwardedFor(
            "203.0.113.10",
            -2);

        assertTrue(request.getClientIp().isEmpty());
    }

    @Test
    void clientIpXForwardedForOffset_defaultsAndRejectsNonNegative() {
        assertEquals(DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET,
            CloudFunctionRequest.clientIpXForwardedForOffset(null));
        assertEquals(DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET,
            CloudFunctionRequest.clientIpXForwardedForOffset("  "));
        assertEquals(DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET,
            CloudFunctionRequest.clientIpXForwardedForOffset("-1"));
        assertEquals(-2,
            CloudFunctionRequest.clientIpXForwardedForOffset("-2"));
        assertThrows(IllegalArgumentException.class,
            () -> CloudFunctionRequest.clientIpXForwardedForOffset("1"));
        assertThrows(IllegalArgumentException.class,
            () -> CloudFunctionRequest.clientIpXForwardedForOffset("nope"));
    }

    private static CloudFunctionRequest requestWithForwardedFor(String forwardedFor, int positionFromEnd) {
        HttpRequest nativeRequest = mock(HttpRequest.class);
        when(nativeRequest.getHeaders()).thenReturn(Map.of(
            "X-Forwarded-For", Lists.newArrayList(forwardedFor)
        ));
        return CloudFunctionRequest.of(nativeRequest, positionFromEnd);
    }

}
