package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpContent;
import com.google.auth.Credentials;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Passes the admin API key for Claude
 *
 * @implNote This could be extended to support different header names, not necessarily x-api-key
 */
@Log
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class ClaudeAuthStrategy implements SourceAuthStrategy {

    @NonNull
    SecretStore secretStore;

    @Getter
    private final String configIdentifier = "claude_admin_api_key";

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        // a 'admin API key' created by admin in Claude
        ADMIN_API_KEY,
    }

    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {

        // in effect, no-credentials
        return new Credentials() {
            @Override
            public String getAuthenticationType() {
                return "";
            }

            @Override
            public Map<String, List<String>> getRequestMetadata() throws IOException {
                String rawAdminApiKey = secretStore.getConfigPropertyAsOptional(ConfigProperty.ADMIN_API_KEY).orElseThrow(
                    () -> new IllegalStateException("ADMIN_API_KEY not configured"));
                String adminApiKey = normalizeAdminApiKey(rawAdminApiKey);

                return ImmutableMap.of("x-api-key", Collections.singletonList(adminApiKey));
            }

            @Override
            public Map<String, List<String>> getRequestMetadata(URI uri) throws IOException {
                return getRequestMetadata();
            }

            @Override
            public boolean hasRequestMetadata() {
                return true;
            }

            @Override
            public boolean hasRequestMetadataOnly() {
                return true;
            }

            @Override
            public void refresh() throws IOException {
                //no-op
            }
        };
    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
            ConfigProperty.ADMIN_API_KEY
        );
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Arrays.stream(ConfigProperty.values()).collect(Collectors.toSet());
    }

    @VisibleForTesting
    static String normalizeAdminApiKey(String raw) {
        String normalized = stripLeadingAndTrailingWhitespaceAndControlChars(raw);
        if (normalized.isEmpty()) {
            throw new IllegalStateException("ADMIN_API_KEY not configured");
        }
        if (!Objects.equals(raw, normalized)) {
            log.log(Level.WARNING,
                "ADMIN_API_KEY contained leading/trailing whitespace or control characters; these were stripped before use");
        }
        return normalized;
    }

    private static boolean isWhitespaceOrControl(char c) {
        return Character.isWhitespace(c) || Character.isISOControl(c);
    }

    private static String stripLeadingAndTrailingWhitespaceAndControlChars(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isWhitespaceOrControl(value.charAt(start))) {
            start++;
        }
        while (end > start && isWhitespaceOrControl(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }
}
