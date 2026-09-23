package co.worklytics.psoxy.gateway;

import com.google.auth.Credentials;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SourceAuthStrategyTest {

    @Test
    void defaultIsSourceAuthFailure_isFalse() {
        SourceAuthStrategy strategy = new SourceAuthStrategy() {
            @Override
            public String getConfigIdentifier() {
                return "test";
            }

            @Override
            public Credentials getCredentials(Optional<String> userToImpersonate) {
                return null;
            }

            @Override
            public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
                return Set.of();
            }

            @Override
            public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
                return Set.of();
            }
        };

        assertFalse(strategy.isSourceAuthFailure(new IOException(
            "Error getting access token for service account: 401 Unauthorized")));
    }
}
