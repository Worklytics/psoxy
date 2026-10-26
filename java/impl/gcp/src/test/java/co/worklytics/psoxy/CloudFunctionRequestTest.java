package co.worklytics.psoxy;

import com.google.cloud.functions.HttpRequest;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static co.worklytics.psoxy.CloudFunctionRequest.CLIENT_X_FORWARDED_FOR_POSITION_DIRECT;
import static co.worklytics.psoxy.CloudFunctionRequest.CLIENT_X_FORWARDED_FOR_POSITION_EXTERNAL_ALB;
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

        CloudFunctionRequest request = CloudFunctionRequest.of(nativeRequest, CLIENT_X_FORWARDED_FOR_POSITION_DIRECT);

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

        CloudFunctionRequest request = CloudFunctionRequest.of(nativeRequest, CLIENT_X_FORWARDED_FOR_POSITION_DIRECT);

        assertTrue(request.isHttps().get());
    }

    @Test
    void getClientIp_positionMinusOne_usesLastHop() {
        CloudFunctionRequest request = requestWithForwardedFor(
            "10.0.0.5, 203.0.113.10",
            CLIENT_X_FORWARDED_FOR_POSITION_DIRECT);

        assertEquals("203.0.113.10", request.getClientIp().get());
    }

    @Test
    void getClientIp_positionMinusOne_singleHopIsThatAddress() {
        CloudFunctionRequest request = requestWithForwardedFor(
            "203.0.113.10",
            CLIENT_X_FORWARDED_FOR_POSITION_DIRECT);

        assertEquals("203.0.113.10", request.getClientIp().get());
    }

    @Test
    void getClientIp_positionMinusTwo_usesSecondToLastHop() {
        CloudFunctionRequest request = requestWithForwardedFor(
            "203.0.113.10, 198.51.100.7",
            CLIENT_X_FORWARDED_FOR_POSITION_EXTERNAL_ALB);

        assertEquals("203.0.113.10", request.getClientIp().get());
    }

    @Test
    void getClientIp_positionMinusTwo_ignoresPrefixBeforeTheSelectedHop() {
        CloudFunctionRequest request = requestWithForwardedFor(
            "10.0.0.5, 198.51.100.7, 203.0.113.10",
            CLIENT_X_FORWARDED_FOR_POSITION_EXTERNAL_ALB);

        assertEquals("198.51.100.7", request.getClientIp().get());
    }

    @Test
    void getClientIp_positionBeyondHeader_isEmpty() {
        CloudFunctionRequest request = requestWithForwardedFor(
            "203.0.113.10",
            CLIENT_X_FORWARDED_FOR_POSITION_EXTERNAL_ALB);

        assertTrue(request.getClientIp().isEmpty());
    }

    @Test
    void clientXForwardedForPosition_defaultsAndRejectsNonNegative() {
        assertEquals(CLIENT_X_FORWARDED_FOR_POSITION_DIRECT,
            CloudFunctionRequest.clientXForwardedForPosition(null));
        assertEquals(CLIENT_X_FORWARDED_FOR_POSITION_DIRECT,
            CloudFunctionRequest.clientXForwardedForPosition("  "));
        assertEquals(CLIENT_X_FORWARDED_FOR_POSITION_DIRECT,
            CloudFunctionRequest.clientXForwardedForPosition("-1"));
        assertEquals(CLIENT_X_FORWARDED_FOR_POSITION_EXTERNAL_ALB,
            CloudFunctionRequest.clientXForwardedForPosition("-2"));
        assertThrows(IllegalArgumentException.class,
            () -> CloudFunctionRequest.clientXForwardedForPosition("1"));
        assertThrows(IllegalArgumentException.class,
            () -> CloudFunctionRequest.clientXForwardedForPosition("nope"));
    }

    private static CloudFunctionRequest requestWithForwardedFor(String forwardedFor, int positionFromEnd) {
        HttpRequest nativeRequest = mock(HttpRequest.class);
        when(nativeRequest.getHeaders()).thenReturn(Map.of(
            "X-Forwarded-For", Lists.newArrayList(forwardedFor)
        ));
        return CloudFunctionRequest.of(nativeRequest, positionFromEnd);
    }

}
