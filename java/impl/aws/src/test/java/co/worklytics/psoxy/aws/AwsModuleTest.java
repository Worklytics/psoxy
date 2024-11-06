package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

class AwsModuleTest {

    @Test
    void asParameterStoreNamespace() {
        assertEquals("PSOXY_GDIRECTORY", AwsModule.asAwsCompliantNamespace("psoxy-gdirectory"));
    }

    //TODO: tests of `nativeConfigService` method
}
