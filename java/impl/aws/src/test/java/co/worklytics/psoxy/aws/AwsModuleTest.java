package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.AWSConfigProperty;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AwsModuleTest {

    @Test
    void asParameterStoreNamespace() {
        assertEquals("PSOXY_GDIRECTORY", AwsModule.asParameterStoreNamespace("psoxy-gdirectory"));
    }

    @Test
    void defaultSharedConfig() {
        EnvVarsConfigService envVars = mock(EnvVarsConfigService.class);
        when(envVars.getConfigPropertyAsOptional(eq(ProxyConfigProperty.PATH_TO_SHARED_CONFIG))).thenReturn(Optional.empty());
        assertNull(AwsModule.parameterStoreConfigService(envVars, mock(SsmClient.class)).getNamespace());
    }

    @Test
    void customSharedConfig() {
        EnvVarsConfigService envVars = mock(EnvVarsConfigService.class);
        when(envVars.getConfigPropertyAsOptional(eq(ProxyConfigProperty.PATH_TO_SHARED_CONFIG))).thenReturn(Optional.of("PSOXY/"));
        assertEquals("PSOXY/", AwsModule.parameterStoreConfigService(envVars, mock(SsmClient.class)).getNamespace());
    }

    @Test
    void customConnectorConfig() {
        EnvVarsConfigService envVars = mock(EnvVarsConfigService.class);

        when(envVars.getConfigPropertyAsOptional(eq(ProxyConfigProperty.PATH_TO_CONNECTOR_CONFIG))).thenReturn(Optional.of("PSOXY_GCAL/"));
        assertEquals("PSOXY_GCAL/", AwsModule.functionParameterStoreConfigService(envVars, mock(SsmClient.class)).getNamespace());
    }
}
