package co.worklytics.psoxy.gateway.impl;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableMap;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.java.Log;

/**
 * HTTP Basic Auth strategy, with user-id + password as credentials.
 * 
 * They get base64 encoded, and sent in the Authorization header as a 'Basic' token.
 * 
 * This is RFC 7617 Section 2
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7617#section-2">RFC 7617 Section 2</a>
 */
@Log
@AllArgsConstructor(onConstructor_ = @Inject)
public class BasicAuthStrategy implements SourceAuthStrategy {

    SecretStore secretStore;

    @Getter
    private final String configIdentifier = "basic_auth";

    enum ConfigProperty implements ConfigService.ConfigProperty {
        BASIC_AUTH_USER_ID, BASIC_AUTH_PASSWORD,
        ;
    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Collections.emptySet(); // no required config properties
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ConfigProperty.values());
    }


    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {
        String userId = secretStore.getConfigPropertyOrError(ConfigProperty.BASIC_AUTH_USER_ID);
        String password = secretStore.getConfigPropertyOrError(ConfigProperty.BASIC_AUTH_PASSWORD);
        return new BasicCredentials(userId, password);
    }

    @AllArgsConstructor
    private static class BasicCredentials extends Credentials {

        /**
         * The user-id to use for basic auth.
         */
        private final String userId;

        /**
         * The password to use for basic auth.
         */
        private final String password;

        @Override
        public String getAuthenticationType() {
            return "basic";
        }

        @Override
        public Map<String, List<String>> getRequestMetadata() throws IOException {

            // per RFC 7617 Section 2, the user-id and password are base64 encoded, and sent in the
            // Authorization header as a 'Basic' token.
            String encoded =
                    Base64.getEncoder().encodeToString((userId + ":" + password).getBytes());

            return ImmutableMap.of("Authorization", Collections.singletonList("Basic " + encoded));
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
            // no-op
        }
    }

}
