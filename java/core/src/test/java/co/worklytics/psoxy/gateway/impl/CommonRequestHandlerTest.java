package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.test.MockModules;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.google.api.client.http.*;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.HttpTesting;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommonRequestHandlerTest {

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        MockModules.ForConfigService.class,
        MockModules.ForSecretStore.class,
        MockModules.ForRules.class,
        MockModules.ForSourceAuthStrategySet.class,
    })
    public interface Container {
        void inject(CommonRequestHandlerTest test);
    }

    @BeforeEach
    public void setup() {
        CommonRequestHandlerTest.Container container = DaggerCommonRequestHandlerTest_Container.create();
        container.inject(this);

        when(handler.secretStore.getConfigPropertyAsOptional(eq(ProxyConfigProperty.PSOXY_SALT)))
            .thenReturn(Optional.of("salt"));
        when(handler.config.getConfigPropertyOrError(eq(ProxyConfigProperty.SOURCE)))
            .thenReturn("gmail");
    }

    @Inject
    CommonRequestHandler handler;

    @Inject
    ReversibleTokenizationStrategy reversibleTokenizationStrategy;

    private static Stream<Arguments> provideRequestToBuildTarget() {
        return Stream.of(
            Arguments.of("/some/path", null, "https://proxyhost.com/some/path"),
            Arguments.of("/some/path", "", "https://proxyhost.com/some/path"),
            // calls left as they come:
            Arguments.of("/some/path", "token=base64%2Ftoken%3D%3D&deleted=true", "https://proxyhost.com/some/path?token=base64%2Ftoken%3D%3D&deleted=true"),
            // double encoded path (some ids in zoom contain / or =)n
            Arguments.of("/some/base64%2Fid%3D%3D/path", "pageNumber=3&value=%2526ampsymbol", "https://proxyhost.com/some/base64%2Fid%3D%3D/path?pageNumber=3&value=%2526ampsymbol"),
            Arguments.of("/v2/past_meetings/%2F1%2Bs5FPqReaG4LXW4WCMDQ%3D%3D","","https://proxyhost.com/v2/past_meetings/%2F1%2Bs5FPqReaG4LXW4WCMDQ%3D%3D")
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRequestToBuildTarget")
    void parseTargetUrlAndDecrypt(String path, String queryString, String expectedProxyCallUrl) {
        HttpEventRequest request = new HttpEventRequest() {
            @Override
            public String getPath() {
                return path;
            }

            @Override
            public Optional<String> getQuery() {
                return Optional.ofNullable(queryString);
            }

            @Override
            public Optional<String> getHeader(String headerName) {
                return Optional.empty();
            }

            @Override
            public Optional<List<String>> getMultiValueHeader(String headerName) {
                return Optional.empty();
            }

            @Override
            public String getHttpMethod() {
                return "GET";
            }

            @Override
            public byte[] getBody() {
                return null;
            }
        };
        when(handler.config.getConfigPropertyOrError(eq(ProxyConfigProperty.TARGET_HOST))).thenReturn("proxyhost.com");

        URL url = new URL(handler.reverseTokenizedUrlComponents(handler.parseRequestedTarget(request)));

        assertEquals(expectedProxyCallUrl, url.toString(), "URLs should match");
    }

    @Test
    void parseOptionsFromRequest() {
        //verify precondition that defaults != LEGACY
        assertEquals(
            PseudonymImplementation.DEFAULT,
            Pseudonymizer.ConfigurationOptions.builder().build().getPseudonymImplementation());

        //prep mock request
        HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
        when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
            .thenReturn(Optional.of(PseudonymImplementation.LEGACY.getHttpHeaderValue()));

        //test parsing options from request
        Optional<PseudonymImplementation> impl = handler.parsePseudonymImplementation(request);

        //verify options were parsed correctly
        assertEquals(PseudonymImplementation.LEGACY, impl.orElseThrow());
    }

    @Test
    void getSanitizerForRequest() {
        //verify precondition that defaults != LEGACY
        assertEquals(
            PseudonymImplementation.DEFAULT,
            Pseudonymizer.ConfigurationOptions.builder().build().getPseudonymImplementation());

        //prep mock request
        HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
        when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
            .thenReturn(Optional.of(PseudonymImplementation.LEGACY.getHttpHeaderValue()));

        assertEquals(PseudonymImplementation.LEGACY,
            handler.getSanitizerForRequest(request).getPseudonymizer().getOptions().getPseudonymImplementation());

        assertEquals(PseudonymImplementation.DEFAULT,
            handler.getSanitizerForRequest(mock(HttpEventRequest.class)).getPseudonymizer().getOptions().getPseudonymImplementation());
    }

    @Test
    void testHeadersPassThrough() throws IOException {
        HttpEventResponse.HttpEventResponseBuilder responseBuilder = HttpEventResponse.builder();

        HttpTransport transport = new MockHttpTransport() {
            @Override
            public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
                return new MockLowLevelHttpRequest() {
                    @Override
                    public LowLevelHttpResponse execute() throws IOException {
                        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
                        response.addHeader(org.apache.http.HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate");
                        response.addHeader(org.apache.http.HttpHeaders.ETAG, "37060cd8c28437060cd8c284");
                        response.addHeader(org.apache.http.HttpHeaders.EXPIRES, "Sat, 04 Dec 2020 16:00:00 GMT");
                        response.addHeader(org.apache.http.HttpHeaders.LAST_MODIFIED, "Mon, 15 Nov 2019 12:00:00 GMT");
                        response.addHeader(org.apache.http.HttpHeaders.RETRY_AFTER, "15");
                        response.addHeader(org.apache.http.HttpHeaders.CONNECTION, "close");
                        response.addHeader("Set-Cookie", "SESSIONID=XYZ; Max-Age=3600; Version=1");
                        response.addHeader("X-RateLimit-Category", "ABC");
                        response.addHeader("X-RateLimit-Remaining", "25600");
                        response.addHeader("X-CustomStuff", "value123");
                        response.addHeader("Link", "https://some-url.com/with_a?link=to_use");
                        response.setStatusCode(200);
                        response.setContentType(Json.MEDIA_TYPE);
                        response.setContent("OK");
                        return response;
                    }
                };
            }
        };
        HttpRequest request = transport.createRequestFactory().buildGetRequest(HttpTesting.SIMPLE_GENERIC_URL);
        HttpResponse response = request.execute();

        handler.passThroughHeaders(responseBuilder, response);

        HttpEventResponse httpEventResponse = responseBuilder.build();

        Map<String, String> headersMap = httpEventResponse.getHeaders();

        Set<String> UNEXPECTED_HEADERS = CommonRequestHandler.normalizeHeaders(
            Set.of("Set-Cookie", org.apache.http.HttpHeaders.CONNECTION, "X-CustomStuff"));

        // 8 headers + content-type
        assertEquals(9, headersMap.size());
        assertTrue(headersMap.keySet().stream().noneMatch(UNEXPECTED_HEADERS::contains));

        assertEquals("no-cache, no-store, max-age=0, must-revalidate", headersMap.get(CommonRequestHandler.normalizeHeader(org.apache.http.HttpHeaders.CACHE_CONTROL)));
        assertEquals("25600", headersMap.get(CommonRequestHandler.normalizeHeader("X-RateLimit-Remaining")));
        assertEquals("ABC", headersMap.get(CommonRequestHandler.normalizeHeader("X-RateLimit-Category")));
        assertEquals("15", headersMap.get(CommonRequestHandler.normalizeHeader(org.apache.http.HttpHeaders.RETRY_AFTER)));
        assertEquals(Json.MEDIA_TYPE, headersMap.get(CommonRequestHandler.normalizeHeader(org.apache.http.HttpHeaders.CONTENT_TYPE)));
    }
}
