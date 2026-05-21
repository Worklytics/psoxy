package co.worklytics.psoxy.gateway;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiModeConfigTest {

    @Test
    void defaultsWhenBuiltDirectly() {
        ApiModeConfig config = ApiModeConfig.builder().build();

        assertEquals(ApiModeConfig.TlsVersions.TLSv1_3, config.getTlsVersion());
        assertEquals(180, config.getRequestTimeoutSeconds());
        assertEquals(30_000, config.getSourceApiConnectTimeoutMs());
        assertEquals(150_000, config.getSourceApiReadTimeoutMs());
    }

    @Test
    void fromConfigService_loadsAllProperties() {
        ConfigService configService = new ConfigService() {
            @Override
            public String getConfigPropertyOrError(ConfigProperty property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
                if (property == ApiModeConfig.ApiModeConfigProperty.TARGET_HOST) {
                    return Optional.of("api.example.com");
                }
                if (property == ApiModeConfig.ApiModeConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER) {
                    return Optional.of("oauth");
                }
                if (property == ApiModeConfig.ApiModeConfigProperty.TLS_VERSION) {
                    return Optional.of(ApiModeConfig.TlsVersions.TLSv1_2);
                }
                if (property == ApiModeConfig.ApiModeConfigProperty.ASYNC_OUTPUT_DESTINATION) {
                    return Optional.of("s3://bucket/async");
                }
                if (property == ApiModeConfig.ApiModeConfigProperty.REQUEST_TIMEOUT_SECONDS) {
                    return Optional.of("240");
                }
                return Optional.empty();
            }
        };

        ApiModeConfig config = ApiModeConfig.fromConfigService(configService);

        assertEquals("api.example.com", config.getTargetHost().orElseThrow());
        assertEquals("oauth", config.getSourceAuthStrategyIdentifier().orElseThrow());
        assertEquals(ApiModeConfig.TlsVersions.TLSv1_2, config.getTlsVersion());
        assertEquals("s3://bucket/async", config.getAsyncOutputDestination().orElseThrow());
        assertEquals(240, config.getRequestTimeoutSeconds());
        assertEquals(210_000, config.getSourceApiReadTimeoutMs());
    }
}
