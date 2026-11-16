package co.worklytics.psoxy.gateway.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ConnectivityFailureMatcher;
import co.worklytics.psoxy.gateway.DependencyServiceNames;
import co.worklytics.psoxy.gateway.JavaNetConnectivityFailures;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.oauth.AccountCredentialsGrantTokenRequestBuilder;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MissingConfigCheckerTest {

    ConfigService config;
    SecretStore secretStore;
    SourceAuthStrategy sourceAuthStrategy;
    MissingConfigChecker checker;

    @BeforeEach
    void setup() {
        config = mock(ConfigService.class);
        secretStore = mock(SecretStore.class);
        sourceAuthStrategy = mock(SourceAuthStrategy.class);
        checker = new MissingConfigChecker(config, secretStore, () -> sourceAuthStrategy,
                new ConnectivityFailureMatcher(Set.of(new JavaNetConnectivityFailures(new DependencyServiceNames()))));
        when(sourceAuthStrategy.getRequiredConfigProperties()).thenReturn(
            Set.of(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID,
                AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_SECRET));
    }

    @Test
    void findMissingRequiredConfigProperties_treatsBlankValuesAsMissing() {
        when(config.getConfigPropertyAsOptional(
                AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID))
            .thenReturn(Optional.of("   "));
        when(secretStore.getConfigPropertyAsOptional(
                AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_SECRET))
            .thenReturn(Optional.of(""));

        Set<String> missing = checker.findMissingRequiredConfigProperties();

        assertEquals(2, missing.size());
        assertTrue(missing.contains("CLIENT_ID"));
        assertTrue(missing.contains("CLIENT_SECRET"));
    }

    @Test
    void findMissingRequiredConfigProperties_rethrowsConnectivityFailures() {
        when(sourceAuthStrategy.getRequiredConfigProperties()).thenThrow(
                new RuntimeException(new UnknownHostException("ssm.us-east-1.amazonaws.com")));

        assertThrows(RuntimeException.class, () -> checker.findMissingRequiredConfigProperties());
    }

    @Test
    void findMissingRequiredConfigProperties_ignoresNonConnectivityFailures() {
        when(sourceAuthStrategy.getRequiredConfigProperties()).thenThrow(new IllegalStateException("not wired"));

        assertTrue(checker.findMissingRequiredConfigProperties().isEmpty());
    }
}
