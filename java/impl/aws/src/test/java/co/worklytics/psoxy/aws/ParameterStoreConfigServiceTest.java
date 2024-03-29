package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.utils.RandomNumberGeneratorImpl;
import lombok.NonNull;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockMakers;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ParameterStoreConfigServiceTest {


    public static boolean isAtLeastJava17() {
        return SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_17);
    }
    SsmClient client;
    @BeforeEach
    public void setup() {
        if (isAtLeastJava17()) {
            client = mock(SsmClient.class, withSettings().mockMaker(MockMakers.SUBCLASS));
        } else {
            client = mock(SsmClient.class);
        }
    }

    @CsvSource({
        ",ACCESS_TOKEN",
        "corp/,/corp/ACCESS_TOKEN",
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



    @Test
    void locking_succeeds() {
        ParameterStoreConfigService parameterStoreConfigService =
            spy(new ParameterStoreConfigService(""));
        // not a great test; doesn't test assumptions about SSM / SSM client - rather just the logic
        // based on those assumptions, which is fairly simple

        //setup test
        parameterStoreConfigService.client = client;
        parameterStoreConfigService.clock = Clock.systemUTC();
        parameterStoreConfigService.randomNumberGenerator = new RandomNumberGeneratorImpl();

        when(parameterStoreConfigService.lockParameterValue()).thenReturn("locked_123");

        when(client.putParameter(any(PutParameterRequest.class)))
            .thenReturn(PutParameterResponse.builder().build());

        // mock delete to reset behavior of Put to not throw ParameterAlreadyExistsException
        when(client.deleteParameter(any(DeleteParameterRequest.class)))
            .thenAnswer((Answer<DeleteParameterResponse>) (invocation) -> {
                when(client.putParameter(any(PutParameterRequest.class)))
                    .thenReturn(PutParameterResponse.builder().build());
                return DeleteParameterResponse.builder().build();
            });
        when(client.getParameter(any(GetParameterRequest.class)))
            .thenReturn(GetParameterResponse.builder()
                .parameter(Parameter.builder()
                    .value("locked_123")
                    .lastModifiedDate(Instant.now())
                    .build())
                .build());


        //acquire succeeds if doesn't exist
        assertTrue(parameterStoreConfigService.acquire("test"));
    }

    @Test
    void locking_fails_other_put_happened() {
        ParameterStoreConfigService parameterStoreConfigService =
            spy(new ParameterStoreConfigService(""));
        // not a great test; doesn't test assumptions about SSM / SSM client - rather just the logic
        // based on those assumptions, which is fairly simple

        //setup test
        parameterStoreConfigService.client = client;
        parameterStoreConfigService.clock = Clock.systemUTC();
        parameterStoreConfigService.randomNumberGenerator = new RandomNumberGeneratorImpl();

        when(parameterStoreConfigService.lockParameterValue()).thenReturn("locked_123");

        when(client.putParameter(any(PutParameterRequest.class)))
            .thenReturn(PutParameterResponse.builder().build());

        // mock delete to reset behavior of Put to not throw ParameterAlreadyExistsException
        when(client.deleteParameter(any(DeleteParameterRequest.class)))
            .thenAnswer((Answer<DeleteParameterResponse>) (invocation) -> {
                when(client.putParameter(any(PutParameterRequest.class)))
                    .thenReturn(PutParameterResponse.builder().build());
                return DeleteParameterResponse.builder().build();
            });
        when(client.getParameter(any(GetParameterRequest.class)))
            .thenReturn(GetParameterResponse.builder()
                .parameter(Parameter.builder()
                    .value("locked_456")
                    .lastModifiedDate(Instant.now())
                    .build())
                .build());

        assertFalse(parameterStoreConfigService.acquire("test"));
    }

    @Test
    void locking_fails_already_exists_and_stale() {
        ParameterStoreConfigService parameterStoreConfigService =
            spy(new ParameterStoreConfigService(""));
        // not a great test; doesn't test assumptions about SSM / SSM client - rather just the logic
        // based on those assumptions, which is fairly simple

        //setup test
        parameterStoreConfigService.client = client;
        parameterStoreConfigService.clock = Clock.systemUTC();
        parameterStoreConfigService.randomNumberGenerator = new RandomNumberGeneratorImpl();

        when(parameterStoreConfigService.lockParameterValue()).thenReturn("locked_123");

        Duration expires = Duration.ofSeconds(10);
        //acquire fails if already exists, and stale
        when(client.putParameter(any(PutParameterRequest.class)))
            .thenThrow(ParameterAlreadyExistsException.builder().build());
        when(client.getParameter(any(GetParameterRequest.class)))
            .thenReturn(GetParameterResponse.builder()
                .parameter(Parameter.builder()
                    .value("locked_456")
                    .lastModifiedDate(Instant.now().minusSeconds(expires.getSeconds() + 1))
                    .build())
                .build());

        assertFalse(parameterStoreConfigService.acquire("test", expires));
    }

    @CsvSource({
        "fill me,true",
        "real value,false",
    })
    @ParameterizedTest
    void placeholder(String value, Boolean isEmpty) {
        ParameterStoreConfigService parameterStoreConfigService =
            new ParameterStoreConfigService("");

        //setup test
        parameterStoreConfigService.client = client;
        parameterStoreConfigService.clock = Clock.systemUTC();
        parameterStoreConfigService.envVarsConfig = new EnvVarsConfigService();

        // returns value
        when(client.getParameter(any(GetParameterRequest.class)))
            .thenReturn(GetParameterResponse.builder()
                .parameter(Parameter.builder()
                    .value(value)
                    .lastModifiedDate(Instant.now())
                    .build())
                .build());

        // verify that even if there's a value, may be 'empty' in some cases
        assertEquals(isEmpty,
            parameterStoreConfigService.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.ACCESS_TOKEN).isEmpty());
    }

}
