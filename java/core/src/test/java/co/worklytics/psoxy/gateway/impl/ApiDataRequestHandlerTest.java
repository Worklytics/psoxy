package co.worklytics.psoxy.gateway.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.net.URL;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.HttpTesting;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.PseudonymizerImplFactory;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.RESTApiSanitizer;
import co.worklytics.psoxy.RESTApiSanitizerFactory;
import co.worklytics.psoxy.gateway.ApiModeConfigProperty;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.impl.RESTApiSanitizerImpl;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.psoxy.rules.msft.PrebuiltSanitizerRules;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
import co.worklytics.test.TestUtils;
import dagger.Component;
import lombok.SneakyThrows;

class ApiDataRequestHandlerTest {

        @Singleton
        @Component(modules = {PsoxyModule.class, MockModules.ForConfigService.class,
                        MockModules.ForSecretStore.class, MockModules.ForRules.class,
                        MockModules.ForSourceAuthStrategySet.class,
                        MockModules.ForHttpTransportFactory.class, MockModules.ForSideOutputs.class,
                        MockModules.ForAsyncApiDataRequestHandler.class,
                        TestModules.ForFixedUUID.class, TestModules.ForFixedClock.class,})
        public interface Container {
                void inject(ApiDataRequestHandlerTest test);
        }

        @Inject
        ApiDataRequestHandler handler;

        @Inject
        RESTRules rules;

        ReversibleTokenizationStrategy reversibleTokenizationStrategy;

        DeterministicTokenizationStrategy deterministicTokenizationStrategy =
                        new Sha256DeterministicTokenizationStrategy("salt");

        UrlSafeTokenPseudonymEncoder pseudonymEncoder = new UrlSafeTokenPseudonymEncoder();

        @Inject
        RESTApiSanitizerFactory sanitizerFactory;

        @Inject
        RulesUtils rulesUtils;

        @Inject
        PseudonymizerImplFactory pseudonymizerImplFactory;

        @Inject
        Clock clock;

        private static Stream<Arguments> provideRequestToBuildTarget() {
                return Stream.of(
                                Arguments.of("/some/path", null, "https://proxyhost.com/some/path"),
                                Arguments.of("/some/path", "", "https://proxyhost.com/some/path"),
                                // calls left as they come:
                                Arguments.of("/some/path",
                                                "token=base64%2Ftoken%3D%3D&deleted=true",
                                                "https://proxyhost.com/some/path?token=base64%2Ftoken%3D%3D&deleted=true"),
                                // double encoded path (some ids in zoom contain / or =)n
                                Arguments.of("/some/base64%2Fid%3D%3D/path",
                                                "pageNumber=3&value=%2526ampsymbol",
                                                "https://proxyhost.com/some/base64%2Fid%3D%3D/path?pageNumber=3&value=%2526ampsymbol"),
                                Arguments.of("/v2/past_meetings/%2F1%2Bs5FPqReaG4LXW4WCMDQ%3D%3D",
                                                "",
                                                "https://proxyhost.com/v2/past_meetings/%2F1%2Bs5FPqReaG4LXW4WCMDQ%3D%3D"));
        }

        @SneakyThrows
        @ParameterizedTest
        @MethodSource("provideRequestToBuildTarget")
        void parseTargetUrlAndDecrypt(String path, String queryString,
                        String expectedProxyCallUrl) {
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
                        public Map<String, List<String>> getHeaders() {
                                return Map.of();
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

                        @Override
                        public Object getUnderlyingRepresentation() {
                                return this;
                        }
                };
                when(handler.config.getConfigPropertyOrError(eq(ApiModeConfigProperty.TARGET_HOST)))
                                .thenReturn("proxyhost.com");

                URL url = new URL(handler.reverseTokenizedUrlComponents(
                                handler.parseRequestedTarget(request)));

                assertEquals(expectedProxyCallUrl, url.toString(), "URLs should match");
        }

        @Test
        void parseOptionsFromRequest() {
                setup("gmail", "google.apis.com");

                // verify precondition that defaults != LEGACY
                assertEquals(PseudonymImplementation.DEFAULT, Pseudonymizer.ConfigurationOptions
                                .builder().build().getPseudonymImplementation());

                // prep mock request
                HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
                when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
                                .thenReturn(Optional.of(PseudonymImplementation.LEGACY
                                                .getHttpHeaderValue()));

                // test parsing options from request
                Optional<PseudonymImplementation> impl =
                                handler.parsePseudonymImplementation(request);

                // verify options were parsed correctly
                assertEquals(PseudonymImplementation.LEGACY, impl.orElseThrow());
        }

        @Test
        @SneakyThrows
        void handleShouldUseOriginalURLWhenIsParametersAreReversed() {
                setup("gmail", "google.apis.com");

                ApiDataRequestHandler spy = spy(handler);
                String original = "blah";
                String encodedPseudonym = pseudonymEncoder.encode(Pseudonym.builder()
                                .hash(deterministicTokenizationStrategy.getToken(original,
                                                Function.identity()))
                                .reversible(reversibleTokenizationStrategy
                                                .getReversibleToken(original, Function.identity()))
                                .build());

                HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
                when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
                                .thenReturn(Optional.of(PseudonymImplementation.DEFAULT
                                                .getHttpHeaderValue()));
                when(request.getHttpMethod()).thenReturn("GET");
                when(request.getPath()).thenReturn("/admin/directory/v1/users/" + encodedPseudonym);
                when(request.getQuery()).thenReturn(Optional.of(
                                "%24select=proxyAddresses%2CotherMails%2ChireDate%2CisResourceAccount%2Cmail%2CemployeeId%2Cid%2CuserType%2CmailboxSettings%2CaccountEnabled"));

                HttpRequestFactory requestFactory = mock(HttpRequestFactory.class);
                when(requestFactory.buildRequest(anyString(), any(), any())).thenReturn(null);
                doReturn(requestFactory).when(spy).getRequestFactory(any());

                RESTApiSanitizerImpl sanitizer = mock(RESTApiSanitizerImpl.class);
                when(sanitizer.isAllowed(anyString(), any(), anyString(), any())).thenReturn(true);
                spy.sanitizer = sanitizer;

                try {
                        spy.handle(request, ApiDataRequestHandler.ProcessingContext
                                        .synchronous(clock.instant()));
                } catch (Exception ignored) {
                        // it should raise an exception due missing configuration
                }

                ArgumentCaptor<URL> urlArgumentCaptor = ArgumentCaptor.forClass(URL.class);
                ArgumentCaptor<GenericUrl> targetUrlArgumentCaptor =
                                ArgumentCaptor.forClass(GenericUrl.class);

                verify(sanitizer).isAllowed(anyString(), urlArgumentCaptor.capture(), anyString(),
                                any());
                verify(requestFactory).buildRequest(anyString(), targetUrlArgumentCaptor.capture(),
                                any());

                // Sanitization should receive original URL requested
                assertEquals("https://google.apis.com/admin/directory/v1/users/" + encodedPseudonym
                                + "?%24select=proxyAddresses%2CotherMails%2ChireDate%2CisResourceAccount%2Cmail%2CemployeeId%2Cid%2CuserType%2CmailboxSettings%2CaccountEnabled",
                                urlArgumentCaptor.getValue().toString());
                // But request done to source should get the URL with the reverse tokens
                assertEquals("https://google.apis.com/admin/directory/v1/users/" + original
                                + "?$select=proxyAddresses,otherMails,hireDate,isResourceAccount,mail,employeeId,id,userType,mailboxSettings,accountEnabled",
                                targetUrlArgumentCaptor.getValue().toString());
        }

        @Test
        @SneakyThrows
        void handleShouldUseOriginalURLWhenIsAllIsReversed() {
                setup("azure-ad", "graph.microsoft.com");

                ApiDataRequestHandler spy = spy(handler);

                String userId = "48d31887-5fad-4d73-a9f5-3c356e68a038";
                String query = "startDateTime=2019-12-30T00:00:00Z&endDateTime=2022-05-16T00:00:00Z&limit=1&$top=1&$skip=1";

                String encodedPseudonym = pseudonymEncoder.encode(Pseudonym.builder()
                                .hash(deterministicTokenizationStrategy.getToken(userId,
                                                Function.identity()))
                                .reversible(reversibleTokenizationStrategy
                                                .getReversibleToken(userId, Function.identity()))
                                .build());

                String originalPath = "/v1.0/users/" + userId + "/calendar/calendarView?" + query;
                HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
                when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
                                .thenReturn(Optional.of(PseudonymImplementation.DEFAULT
                                                .getHttpHeaderValue()));
                when(request.getHttpMethod()).thenReturn("GET");
                when(request.getPath()).thenReturn(
                                "/v1.0/users/" + encodedPseudonym + "/calendar/calendarView");
                when(request.getQuery()).thenReturn(Optional.of(query));

                HttpRequestFactory requestFactory = mock(HttpRequestFactory.class);
                when(requestFactory.buildRequest(anyString(), any(), any())).thenReturn(null);
                doReturn(requestFactory).when(spy).getRequestFactory(any());

                RESTApiSanitizer sanitizer = spy(buildSanitizer(
                                PrebuiltSanitizerRules.MSFT_DEFAULT_RULES_MAP.get("outlook-cal"
                                                + ConfigRulesModule.NO_APP_IDS_SUFFIX)));
                spy.sanitizer = sanitizer;

                try {
                        spy.handle(request, ApiDataRequestHandler.ProcessingContext.builder()
                                        .async(false).requestId("r")
                                        .requestReceivedAt(clock.instant()).build());
                } catch (Exception ignored) {
                        // it should raise an exception due missing configuration
                }

                ArgumentCaptor<URL> urlArgumentCaptor = ArgumentCaptor.forClass(URL.class);
                ArgumentCaptor<GenericUrl> targetUrlArgumentCaptor =
                                ArgumentCaptor.forClass(GenericUrl.class);

                verify(sanitizer, times(1)).isAllowed(anyString(), urlArgumentCaptor.capture(),
                                anyString(), any());
                verify(requestFactory).buildRequest(anyString(), targetUrlArgumentCaptor.capture(),
                                any());

                // Sanitization should receive original URL requested
                assertEquals("https://graph.microsoft.com" + "/v1.0/users/" + encodedPseudonym
                                + "/calendar/calendarView?" + query,
                                urlArgumentCaptor.getValue().toString());
                // But request done to source should get the URL with the reverse tokens
                assertEquals("https://graph.microsoft.com" + "/v1.0/users/" + userId
                                + "/calendar/calendarView?" + query,
                                targetUrlArgumentCaptor.getValue().toString());
        }

        @Test
        @SneakyThrows
        void handleShouldUseOriginalURLWhenIsNotReversed() {
                setup("gmail", "google.apis.com");

                ApiDataRequestHandler spy = spy(handler);
                String original = "blah";

                HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
                when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
                                .thenReturn(Optional.of(PseudonymImplementation.DEFAULT
                                                .getHttpHeaderValue()));
                when(request.getHttpMethod()).thenReturn("GET");
                when(request.getPath()).thenReturn("/admin/directory/v1/users/" + original);
                when(request.getQuery()).thenReturn(Optional.of(
                                "%24select=proxyAddresses%2CotherMails%2ChireDate%2CisResourceAccount%2Cmail%2CemployeeId%2Cid%2CuserType%2CmailboxSettings%2CaccountEnabled"));

                HttpRequestFactory requestFactory = mock(HttpRequestFactory.class);
                when(requestFactory.buildRequest(anyString(), any(), any())).thenReturn(null);
                doReturn(requestFactory).when(spy).getRequestFactory(any());

                RESTApiSanitizerImpl sanitizer = mock(RESTApiSanitizerImpl.class);
                when(sanitizer.isAllowed(anyString(), any(), anyString(), any())).thenReturn(true);

                spy.sanitizer = sanitizer;

                try {
                        spy.handle(request, ApiDataRequestHandler.ProcessingContext
                                        .synchronous(clock.instant()));
                } catch (Exception ignored) {
                        // it should raise an exception due missing configuration
                }

                ArgumentCaptor<URL> urlArgumentCaptor = ArgumentCaptor.forClass(URL.class);
                ArgumentCaptor<GenericUrl> targetUrlArgumentCaptor =
                                ArgumentCaptor.forClass(GenericUrl.class);

                verify(sanitizer).isAllowed(anyString(), urlArgumentCaptor.capture(), anyString(),
                                any());
                verify(requestFactory).buildRequest(anyString(), targetUrlArgumentCaptor.capture(),
                                any());

                // Sanitization should receive original URL requested
                assertEquals("https://google.apis.com/admin/directory/v1/users/" + original
                                + "?%24select=proxyAddresses%2CotherMails%2ChireDate%2CisResourceAccount%2Cmail%2CemployeeId%2Cid%2CuserType%2CmailboxSettings%2CaccountEnabled",
                                urlArgumentCaptor.getValue().toString());
                // But request done to source should get the URL with the reverse tokens
                assertEquals("https://google.apis.com/admin/directory/v1/users/" + original
                                + "?$select=proxyAddresses,otherMails,hireDate,isResourceAccount,mail,employeeId,id,userType,mailboxSettings,accountEnabled",
                                targetUrlArgumentCaptor.getValue().toString());
        }

        @Test
        void getSanitizerForRequest() {
                setup("gmail", "google.apis.com");

                // verify precondition that defaults != LEGACY
                assertEquals(PseudonymImplementation.DEFAULT, Pseudonymizer.ConfigurationOptions
                                .builder().build().getPseudonymImplementation());

                // prep mock request
                HttpEventRequest request = MockModules.provideMock(HttpEventRequest.class);
                when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
                                .thenReturn(Optional.of(PseudonymImplementation.LEGACY
                                                .getHttpHeaderValue()));

                assertEquals(PseudonymImplementation.LEGACY, handler.getSanitizerForRequest(request)
                                .getPseudonymizer().getOptions().getPseudonymImplementation());

                assertEquals(PseudonymImplementation.DEFAULT,
                                handler.getSanitizerForRequest(mock(HttpEventRequest.class))
                                                .getPseudonymizer().getOptions()
                                                .getPseudonymImplementation());
        }

        @Test
        void testHeadersPassThrough() throws IOException {
                setup("gmail", "google.apis.com");

                HttpEventResponse.HttpEventResponseBuilder responseBuilder =
                                HttpEventResponse.builder();

                HttpTransport transport = new MockHttpTransport() {
                        @Override
                        public LowLevelHttpRequest buildRequest(String method, String url)
                                        throws IOException {
                                return new MockLowLevelHttpRequest() {
                                        @Override
                                        public LowLevelHttpResponse execute() throws IOException {
                                                MockLowLevelHttpResponse response =
                                                                new MockLowLevelHttpResponse();
                                                response.addHeader(
                                                                org.apache.http.HttpHeaders.CACHE_CONTROL,
                                                                "no-cache, no-store, max-age=0, must-revalidate");
                                                response.addHeader(org.apache.http.HttpHeaders.ETAG,
                                                                "37060cd8c28437060cd8c284");
                                                response.addHeader(
                                                                org.apache.http.HttpHeaders.EXPIRES,
                                                                "Sat, 04 Dec 2020 16:00:00 GMT");
                                                response.addHeader(
                                                                org.apache.http.HttpHeaders.LAST_MODIFIED,
                                                                "Mon, 15 Nov 2019 12:00:00 GMT");
                                                response.addHeader(
                                                                org.apache.http.HttpHeaders.RETRY_AFTER,
                                                                "15");
                                                response.addHeader(
                                                                org.apache.http.HttpHeaders.CONNECTION,
                                                                "close");
                                                response.addHeader("Set-Cookie",
                                                                "SESSIONID=XYZ; Max-Age=3600; Version=1");
                                                response.addHeader("X-RateLimit-Category", "ABC");
                                                response.addHeader("X-RateLimit-Remaining",
                                                                "25600");
                                                response.addHeader("X-CustomStuff", "value123");
                                                response.addHeader("Link",
                                                                "https://some-url.com/with_a?link=to_use");
                                                response.setStatusCode(200);
                                                response.setContentType(Json.MEDIA_TYPE);
                                                response.setContent("OK");
                                                return response;
                                        }
                                };
                        }
                };
                HttpRequest request = transport.createRequestFactory()
                                .buildGetRequest(HttpTesting.SIMPLE_GENERIC_URL);
                HttpResponse response = request.execute();

                handler.passThroughHeaders(responseBuilder, response);

                HttpEventResponse httpEventResponse = responseBuilder.build();

                Map<String, String> headersMap = httpEventResponse.getHeaders();

                Set<String> UNEXPECTED_HEADERS = ApiDataRequestHandler.normalizeHeaders(
                                Set.of("Set-Cookie", org.apache.http.HttpHeaders.CONNECTION,
                                                "X-CustomStuff"));

                // 8 headers + content-type
                assertEquals(9, headersMap.size());
                assertTrue(headersMap.keySet().stream().noneMatch(UNEXPECTED_HEADERS::contains));

                assertEquals("no-cache, no-store, max-age=0, must-revalidate",
                                headersMap.get(ApiDataRequestHandler.normalizeHeader(
                                                org.apache.http.HttpHeaders.CACHE_CONTROL)));
                assertEquals("25600", headersMap.get(
                                ApiDataRequestHandler.normalizeHeader("X-RateLimit-Remaining")));
                assertEquals("ABC", headersMap.get(
                                ApiDataRequestHandler.normalizeHeader("X-RateLimit-Category")));
                assertEquals("15", headersMap.get(ApiDataRequestHandler
                                .normalizeHeader(org.apache.http.HttpHeaders.RETRY_AFTER)));
                assertEquals(Json.MEDIA_TYPE, headersMap.get(ApiDataRequestHandler
                                .normalizeHeader(org.apache.http.HttpHeaders.CONTENT_TYPE)));
        }

        private void setup(String source, String host) {
                ApiDataRequestHandlerTest.Container container =
                                DaggerApiDataRequestHandlerTest_Container.create();
                container.inject(this);

                when(handler.secretStore
                                .getConfigPropertyAsOptional(eq(ProxyConfigProperty.PSOXY_SALT)))
                                                .thenReturn(Optional.of("salt"));
                when(handler.config.getConfigPropertyOrError(eq(ProxyConfigProperty.SOURCE)))
                                .thenReturn(source);
                when(handler.config.getConfigPropertyOrError(ApiModeConfigProperty.TARGET_HOST))
                                .thenReturn(host);

                reversibleTokenizationStrategy =
                                AESReversibleTokenizationStrategy.builder()
                                                .cipherSuite(AESReversibleTokenizationStrategy.CBC)
                                                .key(TestUtils.testKey())
                                                .deterministicTokenizationStrategy(
                                                                deterministicTokenizationStrategy)
                                                .build();
        }

        private RESTApiSanitizer buildSanitizer(RESTRules rules) {
                Pseudonymizer defaultPseudonymizer = pseudonymizerImplFactory
                                .create(Pseudonymizer.ConfigurationOptions.builder()
                                                .pseudonymImplementation(
                                                                PseudonymImplementation.DEFAULT)
                                                .build());

                return sanitizerFactory.create(rules, defaultPseudonymizer);
        }
}
