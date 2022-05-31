package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.test.MockModules;
import com.google.common.collect.ImmutableMap;
import dagger.Component;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class CommonRequestHandlerTest {

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        MockModules.ForConfigService.class,
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
    }

    @Inject
    CommonRequestHandler handler;

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
    void buildTarget(String path, String queryString, String expectedProxyCallUrl) {
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
        };
        when(handler.config.getConfigPropertyOrError(eq(ProxyConfigProperty.TARGET_HOST))).thenReturn("proxyhost.com");

        URL url = handler.buildTarget(request);

        assertEquals(expectedProxyCallUrl, url.toString(), "URLs should match");
    }

    @SneakyThrows
    @Test
    @Tag("integration test") // does a real HTTP call to https://dummy.restapiexample.com/api/v1/employees
    void compress() {
        HttpEventRequest request = new HttpEventRequest() {
            @Override
            public String getPath() {
                return "/api/v1/employees";
            }

            @Override
            public Optional<String> getQuery() {
                return Optional.empty();
            }

            @Override
            public Optional<String> getHeader(String headerName) {
                return Optional.ofNullable(ImmutableMap.of("x-psoxy-skip-sanitizer", "true",
                    "accept-encoding","gzip").get(headerName.toLowerCase()));
            }

            @Override
            public Optional<List<String>> getMultiValueHeader(String headerName) {
                return Optional.empty();
            }

            @Override
            public String getHttpMethod() {
                return "GET";
            }
        };
        when(handler.config.isDevelopment()).thenReturn(true);
        when(handler.config.getConfigPropertyOrError(eq(ProxyConfigProperty.TARGET_HOST))).thenReturn("dummy.restapiexample.com");
        when(handler.config.getConfigPropertyOrError(eq(ProxyConfigProperty.PSOXY_SALT))).thenReturn("anything");
        when(handler.config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.PSOXY_SALT))).thenReturn(Optional.of("anything"));
        when(handler.config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.IDENTIFIER_SCOPE_ID))).thenReturn(Optional.of("hris"));

        HttpEventResponse handle = handler.handle(request);

        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(Base64.decodeBase64(handle.getBody())));

        String compressed = handle.getBody();
        String uncompressed = new String(gzipInputStream.readAllBytes(), StandardCharsets.UTF_8);

        assertFalse(compressed.contains("success")); // should be encoded!
        assertTrue(uncompressed.contains("success")); // should be in plain!
        assertTrue(uncompressed.contains("Successfully! All records has been fetched.")); // should be in plain!
        assertTrue(compressed.getBytes().length < uncompressed.getBytes().length);
    }
}
