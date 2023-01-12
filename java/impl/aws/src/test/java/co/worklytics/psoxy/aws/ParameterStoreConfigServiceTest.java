package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.services.ssm.SsmClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ParameterStoreConfigServiceTest {



    @CsvSource({
        ",ACCESS_TOKEN",
        "corp/,corp/ACCESS_TOKEN",
        "/corp/,/corp/ACCESS_TOKEN",
        "PSOXY_GCAL_,PSOXY_GCAL_ACCESS_TOKEN"
    })
    @ParameterizedTest
    void parameterName(String namespace, String expectedParameterName) {

        ParameterStoreConfigService parameterStoreConfigService =
            new ParameterStoreConfigService(namespace);

        assertEquals(
            expectedParameterName,
            parameterStoreConfigService.parameterName(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.ACCESS_TOKEN));
    }
}
