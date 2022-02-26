package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.test.MockModules;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            // encoded query string
            Arguments.of("/some/path", "token=base64%2Ftoken%3D%3D&deleted=true", "https://proxyhost.com/some/path?token=base64/token==&deleted=true"),
            // double encoded path (some ids in zoom contain / or =)
            // path is kept the same as it gets encoded upon creation
            Arguments.of("/some/base64%252Fid%253D%253D/path", "pageNumber=3&value=%2526ampsymbol", "https://proxyhost.com/some/base64%252Fid%253D%253D/path?pageNumber=3&value=%2526ampsymbol")
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
}
