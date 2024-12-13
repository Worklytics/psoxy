package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.impl.RESTApiSanitizerImpl;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.psoxy.rules.msft.PrebuiltSanitizerRules;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.BulkDataRules;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.RuleSet;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import com.google.api.client.http.*;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.HttpTesting;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    @Inject
    CommonRequestHandler handler;

    @Inject
    RESTRules rules;

    ReversibleTokenizationStrategy reversibleTokenizationStrategy;

    DeterministicTokenizationStrategy deterministicTokenizationStrategy = new Sha256DeterministicTokenizationStrategy("salt");

    UrlSafeTokenPseudonymEncoder pseudonymEncoder = new UrlSafeTokenPseudonymEncoder();

    @Inject
    RESTApiSanitizerFactory sanitizerFactory;

    @Inject
    RulesUtils rulesUtils;

    @Inject
    PseudonymizerImplFactory pseudonymizerImplFactory;

    private static Stream<Arguments> provideRequestToBuildTarget() {
        return Stream.of(
                Arguments.of("/some/path", null, "https://proxyhost.com/some/path"),
                Arguments.of("/some/path", "", "https://proxyhost.com/some/path"),
                // calls left as they come:
                Arguments.of("/some/path", "token=base64%2Ftoken%3D%3D&deleted=true", "https://proxyhost.com/some/path?token=base64%2Ftoken%3D%3D&deleted=true"),
                // double encoded path (some ids in zoom contain / or =)n
                Arguments.of("/some/base64%2Fid%3D%3D/path", "pageNumber=3&value=%2526ampsymbol", "https://proxyhost.com/some/base64%2Fid%3D%3D/path?pageNumber=3&value=%2526ampsymbol"),
                Arguments.of("/v2/past_meetings/%2F1%2Bs5FPqReaG4LXW4WCMDQ%3D%3D", "", "https://proxyhost.com/v2/past_meetings/%2F1%2Bs5FPqReaG4LXW4WCMDQ%3D%3D")
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRequestToBuildTarget")
    void parseTargetUrlAndDecrypt(String path, String queryString, String expectedProxyCallUrl) {
        setup("gmail", "google.apis.com");

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

            @Override
            public Optional<String> getClientIp() {
                return Optional.of("127.0.0.1");
            }

            @Override
            public Optional<Boolean> isHttps() {
                return Optional.empty();
            }
        };
        when(handler.config.getConfigPropertyOrError(eq(ProxyConfigProperty.TARGET_HOST))).thenReturn("proxyhost.com");

        URL url = new URL(handler.reverseTokenizedUrlComponents(handler.parseRequestedTarget(request)));

        assertEquals(expectedProxyCallUrl, url.toString(), "URLs should match");
    }

    @Test
    void parseOptionsFromRequest() {
        setup("gmail", "google.apis.com");

        //verify precondition that defaults != LEGACY
        assertEquals(
                PseudonymImplementation.DEFAULT,
                Pseudonymizer.ConfigurationOptions.builder().build().getPseudonymImplementation());

        //prep mock request
        HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
        when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
                .thenReturn(Optional.of(PseudonymImplementation.DEFAULT.getHttpHeaderValue()));

        //test parsing options from request
        Optional<PseudonymImplementation> impl = handler.parsePseudonymImplementation(request);

        //verify options were parsed correctly
        assertEquals(PseudonymImplementation.DEFAULT, impl.orElseThrow());
    }

    @Test
    @SneakyThrows
    void handleShouldUseOriginalURLWhenIsParametersAreReversed() {
        setup("gmail", "google.apis.com");

        CommonRequestHandler spy = spy(handler);
        String original = "blah";
        String encodedPseudonym =
                pseudonymEncoder.encode(Pseudonym.builder()
                        .hash(deterministicTokenizationStrategy.getToken(original, Function.identity()))
                        .reversible(reversibleTokenizationStrategy.getReversibleToken(original, Function.identity())).build());

        HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
        when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
                .thenReturn(Optional.of(PseudonymImplementation.DEFAULT.getHttpHeaderValue()));
        when(request.getHttpMethod())
                .thenReturn("GET");
        when(request.getPath())
                .thenReturn("/admin/directory/v1/users/" + encodedPseudonym);
        when(request.getQuery())
                .thenReturn(Optional.of("%24select=proxyAddresses%2CotherMails%2ChireDate%2CisResourceAccount%2Cmail%2CemployeeId%2Cid%2CuserType%2CmailboxSettings%2CaccountEnabled"));

        HttpRequestFactory requestFactory = mock(HttpRequestFactory.class);
        when(requestFactory.buildRequest(anyString(), any(), any()))
                .thenReturn(null);
        doReturn(requestFactory).when(spy).getRequestFactory(any());

        RESTApiSanitizerImpl sanitizer = mock(RESTApiSanitizerImpl.class);
        when(sanitizer.isAllowed(anyString(), any()))
                .thenReturn(true);
        spy.sanitizer = sanitizer;

        try {
            spy.handle(request);
        } catch (Exception ignored) {
            // it should raise an exception due missing configuration
        }

        ArgumentCaptor<URL> urlArgumentCaptor = ArgumentCaptor.forClass(URL.class);
        ArgumentCaptor<GenericUrl> targetUrlArgumentCaptor = ArgumentCaptor.forClass(GenericUrl.class);

        verify(sanitizer).isAllowed(anyString(), urlArgumentCaptor.capture());
        verify(requestFactory).buildRequest(anyString(), targetUrlArgumentCaptor.capture(), any());

        // Sanitization should receive original URL requested
        assertEquals("https://google.apis.com/admin/directory/v1/users/" + encodedPseudonym + "?%24select=proxyAddresses%2CotherMails%2ChireDate%2CisResourceAccount%2Cmail%2CemployeeId%2Cid%2CuserType%2CmailboxSettings%2CaccountEnabled",
                urlArgumentCaptor.getValue().toString());
        // But request done to source should get the URL with the reverse tokens
        assertEquals("https://google.apis.com/admin/directory/v1/users/" + original + "?$select=proxyAddresses,otherMails,hireDate,isResourceAccount,mail,employeeId,id,userType,mailboxSettings,accountEnabled",
                targetUrlArgumentCaptor.getValue().toString());
    }

    @Test
    @SneakyThrows
    void handleShouldUseOriginalURLWhenIsAllIsReversed() {
        setup("azure-ad", "graph.microsoft.com");

        CommonRequestHandler spy = spy(handler);

        String userId = "48d31887-5fad-4d73-a9f5-3c356e68a038";
        String query = "startDateTime=2019-12-30T00:00:00Z&endDateTime=2022-05-16T00:00:00Z&limit=1&$top=1&$skip=1";

        String encodedPseudonym =
                pseudonymEncoder.encode(Pseudonym.builder()
                        .hash(deterministicTokenizationStrategy.getToken(userId, Function.identity()))
                        .reversible(reversibleTokenizationStrategy.getReversibleToken(userId, Function.identity())).build());

        String originalPath = "/v1.0/users/" + userId + "/calendar/calendarView?" + query;
        HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
        when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
                .thenReturn(Optional.of(PseudonymImplementation.DEFAULT.getHttpHeaderValue()));
        when(request.getHttpMethod())
                .thenReturn("GET");
        when(request.getPath())
                .thenReturn("/v1.0/users/" + encodedPseudonym + "/calendar/calendarView");
        when(request.getQuery())
                .thenReturn(Optional.of(query));

        HttpRequestFactory requestFactory = mock(HttpRequestFactory.class);
        when(requestFactory.buildRequest(anyString(), any(), any()))
                .thenReturn(null);
        doReturn(requestFactory).when(spy).getRequestFactory(any());

        RESTApiSanitizer sanitizer = spy(buildSanitizer(PrebuiltSanitizerRules.MSFT_DEFAULT_RULES_MAP.get("outlook-cal"+ ConfigRulesModule.NO_APP_IDS_SUFFIX)));
        spy.sanitizer = sanitizer;

        try {
            spy.handle(request);
        } catch (Exception ignored) {
            // it should raise an exception due missing configuration
        }

        ArgumentCaptor<URL> urlArgumentCaptor = ArgumentCaptor.forClass(URL.class);
        ArgumentCaptor<GenericUrl> targetUrlArgumentCaptor = ArgumentCaptor.forClass(GenericUrl.class);

        verify(sanitizer, times(1)).isAllowed(anyString(), urlArgumentCaptor.capture());
        verify(requestFactory).buildRequest(anyString(), targetUrlArgumentCaptor.capture(), any());

        // Sanitization should receive original URL requested
        assertEquals("https://graph.microsoft.com" + "/v1.0/users/" + encodedPseudonym + "/calendar/calendarView?" + query,
                urlArgumentCaptor.getValue().toString());
        // But request done to source should get the URL with the reverse tokens
        assertEquals("https://graph.microsoft.com" + "/v1.0/users/" + userId + "/calendar/calendarView?" + query,
                targetUrlArgumentCaptor.getValue().toString());
    }

    @Test
    @SneakyThrows
    void handleShouldUseOriginalURLWhenIsNotReversed() {
        setup("gmail", "google.apis.com");

        CommonRequestHandler spy = spy(handler);
        String original = "blah";

        HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
        when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
                .thenReturn(Optional.of(PseudonymImplementation.DEFAULT.getHttpHeaderValue()));
        when(request.getHttpMethod())
                .thenReturn("GET");
        when(request.getPath())
                .thenReturn("/admin/directory/v1/users/" + original);
        when(request.getQuery())
                .thenReturn(Optional.of("%24select=proxyAddresses%2CotherMails%2ChireDate%2CisResourceAccount%2Cmail%2CemployeeId%2Cid%2CuserType%2CmailboxSettings%2CaccountEnabled"));

        HttpRequestFactory requestFactory = mock(HttpRequestFactory.class);
        when(requestFactory.buildRequest(anyString(), any(), any()))
                .thenReturn(null);
        doReturn(requestFactory).when(spy).getRequestFactory(any());

        RESTApiSanitizerImpl sanitizer = mock(RESTApiSanitizerImpl.class);
        when(sanitizer.isAllowed(anyString(), any()))
                .thenReturn(true);

        spy.sanitizer = sanitizer;

        try {
            spy.handle(request);
        } catch (Exception ignored) {
            // it should raise an exception due missing configuration
        }

        ArgumentCaptor<URL> urlArgumentCaptor = ArgumentCaptor.forClass(URL.class);
        ArgumentCaptor<GenericUrl> targetUrlArgumentCaptor = ArgumentCaptor.forClass(GenericUrl.class);

        verify(sanitizer).isAllowed(anyString(), urlArgumentCaptor.capture());
        verify(requestFactory).buildRequest(anyString(), targetUrlArgumentCaptor.capture(), any());

        // Sanitization should receive original URL requested
        assertEquals("https://google.apis.com/admin/directory/v1/users/" + original + "?%24select=proxyAddresses%2CotherMails%2ChireDate%2CisResourceAccount%2Cmail%2CemployeeId%2Cid%2CuserType%2CmailboxSettings%2CaccountEnabled",
                urlArgumentCaptor.getValue().toString());
        // But request done to source should get the URL with the reverse tokens
        assertEquals("https://google.apis.com/admin/directory/v1/users/" + original + "?$select=proxyAddresses,otherMails,hireDate,isResourceAccount,mail,employeeId,id,userType,mailboxSettings,accountEnabled",
                targetUrlArgumentCaptor.getValue().toString());
    }

    @Test
    void getSanitizerForRequest() {
        setup("gmail", "google.apis.com");

        //verify precondition that defaults != LEGACY
        assertEquals(
                PseudonymImplementation.DEFAULT,
                Pseudonymizer.ConfigurationOptions.builder().build().getPseudonymImplementation());

        //prep mock request
        HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
        when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
                .thenReturn(Optional.of(PseudonymImplementation.DEFAULT.getHttpHeaderValue()));

        assertEquals(PseudonymImplementation.DEFAULT,
                handler.getSanitizerForRequest(request).getPseudonymizer().getOptions().getPseudonymImplementation());

        assertEquals(PseudonymImplementation.DEFAULT,
                handler.getSanitizerForRequest(mock(HttpEventRequest.class)).getPseudonymizer().getOptions().getPseudonymImplementation());
    }

    @Test
    void testHeadersPassThrough() throws IOException {
        setup("gmail", "google.apis.com");

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

    private void setup(String source, String host) {
        CommonRequestHandlerTest.Container container = DaggerCommonRequestHandlerTest_Container.create();
        container.inject(this);

        when(handler.secretStore.getConfigPropertyAsOptional(eq(ProxyConfigProperty.PSOXY_SALT)))
                .thenReturn(Optional.of("salt"));
        when(handler.config.getConfigPropertyOrError(eq(ProxyConfigProperty.SOURCE)))
                .thenReturn(source);
        when(handler.config.getConfigPropertyOrError(ProxyConfigProperty.TARGET_HOST))
                .thenReturn(host);

        reversibleTokenizationStrategy = AESReversibleTokenizationStrategy.builder()
                .cipherSuite(AESReversibleTokenizationStrategy.CBC)
                .key(TestUtils.testKey())
                .deterministicTokenizationStrategy(deterministicTokenizationStrategy)
                .build();
    }

    private RESTApiSanitizer buildSanitizer(RESTRules rules) {
        Pseudonymizer defaultPseudonymizer =
                pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                        .pseudonymImplementation(PseudonymImplementation.DEFAULT)
                        .build());

        return sanitizerFactory.create(rules, defaultPseudonymizer);
    }
}
