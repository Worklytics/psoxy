package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.HealthCheckResult;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.ApiModeConfig;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
import dagger.Component;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.worklytics.psoxy.gateway.NetworkSecurityUtils;
import co.worklytics.psoxy.gateway.WebhookCollectorModeConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class HealthCheckRequestHandlerTest {
    @Singleton
    @Component(modules = {
            PsoxyModule.class,
        MockModules.ForOpenNlp.class,
            TestModules.ForApiModeConfig.class,
            TestModules.ForWebhookCollectorModeConfig.class,
            MockModules.ForConfigService.class,
            MockModules.ForSecretStore.class,
            MockModules.ForRules.class,
            MockModules.ForSourceAuthStrategySet.class,
            TestModules.ForProxyConstants.class,

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

    @Inject
    ApiModeConfig apiModeConfig;

    HttpEventRequest request;

    @Test
    void handleIfHealthCheck_should_serialize_response() throws IOException {
        when(request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()))
                .thenReturn(Optional.of(""));

        when(apiModeConfig.getTargetHost())
                .thenReturn(Optional.of("host"));

        Optional<HttpEventResponse> response = handler.handleIfHealthCheck(request);

        assertTrue(response.isPresent());
        HealthCheckResult result = handler.objectMapper.readValue(response.get().getBody(), HealthCheckResult.class);
        assertEquals("something", result.getConfiguredSource());
        assertEquals("host", result.getConfiguredHost());
        assertTrue(result.getNonDefaultSalt());
        assertEquals(Collections.emptySet(), result.getMissingConfigProperties());
        assertTrue(StringUtils.isNotBlank(result.getSaltSha256Hash()));
        assertTrue(result.passed());

        assertEquals(HttpStatus.SC_OK, response.get().getStatusCode());
    }

    @Test
    void handleIfHealthCheck_should_return_invalid_response_when_target_host_is_not_configured() {

        when(request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()))
                .thenReturn(Optional.of(""));

        when(apiModeConfig.getTargetHost())
                .thenReturn(Optional.empty());

        Optional<HttpEventResponse> response = handler.handleIfHealthCheck(request);

        assertTrue(response.isPresent());

        assertEquals(HttpStatus.SC_PRECONDITION_FAILED, response.get().getStatusCode());
    }

    @Test
    void handleIfHealthCheck_should_return_invalid_response_when_target_host_is_empty() {

        when(request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()))
                .thenReturn(Optional.of(""));

        when(apiModeConfig.getTargetHost())
                .thenReturn(Optional.of(""));

        Optional<HttpEventResponse> response = handler.handleIfHealthCheck(request);

        assertTrue(response.isPresent());

        assertEquals(HttpStatus.SC_PRECONDITION_FAILED, response.get().getStatusCode());
    }

    @Test
    void handleIfHealthCheck_should_return_invalid_response_when_target_host_uses_http() {

        when(request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()))
                .thenReturn(Optional.of(""));

        when(apiModeConfig.getTargetHost())
                .thenReturn(Optional.of("http://mycompany.com/gitlab"));

        Optional<HttpEventResponse> response = handler.handleIfHealthCheck(request);

        assertTrue(response.isPresent());

        assertEquals(HttpStatus.SC_PRECONDITION_FAILED, response.get().getStatusCode());
    }

  private void withIpAllowlist(List<String> allowedBlocks) throws Exception {
        when(apiModeConfig.getAllowedDataAccessIpBlocks()).thenReturn(allowedBlocks);
        NetworkSecurityUtils networkSecurityUtils = new NetworkSecurityUtils(
                ApiModeConfig.builder().allowedDataAccessIpBlocks(allowedBlocks).build(),
                WebhookCollectorModeConfig.builder().build());
        Field field = HealthCheckRequestHandler.class.getDeclaredField("networkSecurityUtils");
        field.setAccessible(true);
        field.set(handler, networkSecurityUtils);
    }

    @Test
    void handleIfHealthCheck_should_fail_when_client_ip_not_in_allowlist() throws Exception {
        when(request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()))
                .thenReturn(Optional.of(""));
        when(request.getClientIp()).thenReturn(Optional.of("203.0.113.1"));
        when(apiModeConfig.getTargetHost()).thenReturn(Optional.of("host"));
        withIpAllowlist(List.of("10.0.0.1"));

        Optional<HttpEventResponse> response = handler.handleIfHealthCheck(request);

        assertTrue(response.isPresent());
        HealthCheckResult result = handler.objectMapper.readValue(response.get().getBody(), HealthCheckResult.class);
        assertFalse(result.getClientIpAuthorized());
        assertFalse(result.passed());
        assertTrue(result.getWarningMessages().stream()
                .anyMatch(m -> m.contains("not authorized")));
        assertEquals(HttpStatus.SC_PRECONDITION_FAILED, response.get().getStatusCode());
    }

    @Test
    void handleIfHealthCheck_should_pass_when_client_ip_in_allowlist() throws Exception {
        when(request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()))
                .thenReturn(Optional.of(""));
        when(request.getClientIp()).thenReturn(Optional.of("10.0.0.1"));
        when(apiModeConfig.getTargetHost()).thenReturn(Optional.of("host"));
        withIpAllowlist(List.of("10.0.0.1"));

        Optional<HttpEventResponse> response = handler.handleIfHealthCheck(request);

        assertTrue(response.isPresent());
        HealthCheckResult result = handler.objectMapper.readValue(response.get().getBody(), HealthCheckResult.class);
        assertTrue(result.getClientIpAuthorized());
        assertTrue(result.passed());
        assertEquals(HttpStatus.SC_OK, response.get().getStatusCode());
    }
}
