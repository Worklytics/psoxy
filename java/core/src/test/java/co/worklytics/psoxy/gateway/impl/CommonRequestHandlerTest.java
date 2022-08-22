package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import co.worklytics.test.MockModules;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.mock;
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

    @Test
    void parseOptionsFromRequest() {
        //verify precondition that defaults != LEGACY
        assertEquals(
            PseudonymImplementation.DEFAULT,
            Sanitizer.ConfigurationOptions.builder().build().getPseudonymImplementation());

        //prep mock request
        HttpEventRequest request = mock(HttpEventRequest.class);
        when(request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader()))
            .thenReturn(Optional.of(PseudonymImplementation.LEGACY.getHttpHeaderValue()));

        //test parsing options from request
        Optional<PseudonymImplementation> impl = handler.parsePseudonymImplementation(request);

        //verify options were parsed correctly
        assertEquals(PseudonymImplementation.LEGACY, impl.get());
    }
}
