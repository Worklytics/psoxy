package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.test.MockModules;
import dagger.Component;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HealthCheckRequestHandlerTest {
    @Singleton
    @Component(modules = {
            PsoxyModule.class,
            MockModules.ForConfigService.class,
            MockModules.ForSecretStore.class,
            MockModules.ForRules.class,
            MockModules.ForSourceAuthStrategySet.class,

    })
    public interface Container {
        void inject(HealthCheckRequestHandlerTest test);
    }

    @BeforeEach
    public void setup() {
        HealthCheckRequestHandlerTest.Container container = DaggerHealthCheckRequestHandlerTest_Container.create();
        container.inject(this);

        when(handler.secretStore.getConfigPropertyAsOptional(eq(ProxyConfigProperty.PSOXY_SALT)))
                .thenReturn(Optional.of("salt"));

        when(handler.config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE))
                .thenReturn(Optional.of("something"));

        request = MockModules.provideMock(HttpEventRequest.class);
    }

    @Inject
    HealthCheckRequestHandler handler;

    HttpEventRequest request;

    @Test
    void handleIfHealthCheck_should_serialize_response() {
        when(request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()))
                .thenReturn(Optional.of(""));

        when(handler.config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.TARGET_HOST)))
                .thenReturn(Optional.of("host"));

        Optional<HttpEventResponse> response = handler.handleIfHealthCheck(request);

        assertTrue(response.isPresent());

        assertEquals(HttpStatus.SC_OK, response.get().getStatusCode());
    }

    @Test
    void handleIfHealthCheck_should_return_invalid_response_when_target_host_is_not_configured() {

        when(request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()))
                .thenReturn(Optional.of(""));

        when(handler.config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.TARGET_HOST)))
                .thenReturn(Optional.empty());

        Optional<HttpEventResponse> response = handler.handleIfHealthCheck(request);

        assertTrue(response.isPresent());

        assertEquals(HttpStatus.SC_PRECONDITION_FAILED, response.get().getStatusCode());
    }

    @Test
    void handleIfHealthCheck_should_return_invalid_response_when_target_host_is_empty() {

        when(request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()))
                .thenReturn(Optional.of(""));

        when(handler.config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.TARGET_HOST)))
                .thenReturn(Optional.of(""));

        Optional<HttpEventResponse> response = handler.handleIfHealthCheck(request);

        assertTrue(response.isPresent());

        assertEquals(HttpStatus.SC_PRECONDITION_FAILED, response.get().getStatusCode());
    }
}
