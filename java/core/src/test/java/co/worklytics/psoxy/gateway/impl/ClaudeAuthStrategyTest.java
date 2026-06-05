package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.test.MockModules;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class ClaudeAuthStrategyTest {

    @Test
    void normalizeAdminApiKey_unchangedWhenClean() {
        assertEquals("sk-admin-key", ClaudeAuthStrategy.normalizeAdminApiKey("sk-admin-key"));
    }

    @ParameterizedTest
    @CsvSource({
        "'  sk-admin-key', sk-admin-key",
        "'sk-admin-key  ', sk-admin-key",
        "'\nsk-admin-key\n', sk-admin-key",
        "'\u0000sk-admin-key\u0000', sk-admin-key",
    })
    void normalizeAdminApiKey_stripsLeadingAndTrailingWhitespaceAndControlChars(String raw, String expected) {
        assertEquals(expected, ClaudeAuthStrategy.normalizeAdminApiKey(raw));
    }

    @Test
    void normalizeAdminApiKey_throwsWhenOnlyWhitespace() {
        assertThrows(IllegalStateException.class, () -> ClaudeAuthStrategy.normalizeAdminApiKey("  \n\t"));
    }

    @SneakyThrows
    @Test
    void getRequestMetadata_usesNormalizedKey() {
        SecretStore secretStore = MockModules.provideMock(SecretStore.class);
        when(secretStore.getConfigPropertyAsOptional(ClaudeAuthStrategy.ConfigProperty.ADMIN_API_KEY))
            .thenReturn(java.util.Optional.of("  sk-admin-key\n"));

        ClaudeAuthStrategy strategy = new ClaudeAuthStrategy(secretStore);

        Map<String, List<String>> metadata = strategy.getCredentials(java.util.Optional.empty())
            .getRequestMetadata();

        assertEquals(List.of("sk-admin-key"), metadata.get("x-api-key"));
    }
}
