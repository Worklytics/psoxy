package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
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

    //TODO: tests of `nativeConfigService` method
}
