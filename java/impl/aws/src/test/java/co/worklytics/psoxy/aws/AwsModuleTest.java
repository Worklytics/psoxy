package co.worklytics.psoxy.aws;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AwsModuleTest {

    @Test
    void asParameterStoreNamespace() {
        assertEquals("PSOXY_GDIRECTORY", AwsModule.asParameterStoreNamespace("psoxy-gdirectory"));
    }
}
