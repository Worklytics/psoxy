package co.worklytics.psoxy;

import com.google.cloud.functions.HttpRequest;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.util.Map;

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

        CloudFunctionRequest request = CloudFunctionRequest.of(nativeRequest);

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

        CloudFunctionRequest request = CloudFunctionRequest.of(nativeRequest);

        assertTrue(request.isHttps().get());
    }

    @Test
    void getClientIp_usesFirstForwardedForHop() {
        HttpRequest nativeRequest = mock(HttpRequest.class);

        when(nativeRequest.getHeaders()).thenReturn(Map.of(
            "X-Forwarded-For", Lists.newArrayList("203.0.113.10, 198.51.100.7")
        ));

        CloudFunctionRequest request = CloudFunctionRequest.of(nativeRequest);

        assertEquals("203.0.113.10", request.getClientIp().get());
    }

}
