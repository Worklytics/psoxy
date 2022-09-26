package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.SerializationUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * source auth strategy to authenticate using a short-lived OAuth 2.0 access token which must be
 * periodically refreshed.
 *   Options for refresh method are configured by
 * <p>
 * A new access token will be retrieved for every psoxy instance that spins up; as well as when the
 * current one expires.  We'll endeavor to minimize the number of token requests by sharing this
 * states across API requests
 * <p>
 * If the source API you're connecting to offers long-lived access tokens (or does not offer refresh
 * tokens), you may opt for the access-token only strategy:
 * @see OAuthAccessTokenSourceAuthStrategy
 *
 */
@Log
@NoArgsConstructor(onConstructor_ = @Inject)
public class OAuthRefreshTokenSourceAuthStrategy implements SourceAuthStrategy {

    @Getter
    private final String configIdentifier = "oauth2_refresh_token";

    @Inject
    ConfigService config;

    /**
     * default access token expiration to assume, if 'expires_in' value is omitted from response
     * (which is allowed under OAuth 2.0 spec)
     */
    public static final Duration DEFAULT_ACCESS_TOKEN_EXPIRATION = Duration.ofHours(1);


    //q: should we put these as config properties? creates potential for inconsistent configs
    // eg, orphaned config properties for SourceAuthStrategy not in use; missing config properties
    //  expected by this
    public enum ConfigProperty implements ConfigService.ConfigProperty {
        REFRESH_ENDPOINT,
        CLIENT_ID,
        GRANT_TYPE,
        SHARED_TOKEN,
        WRITABLE_ACCESS_TOKEN,
    }

    @Inject OAuth2CredentialsWithRefresh.OAuth2RefreshHandler refreshHandler;

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        Stream<ConfigService.ConfigProperty> propertyStream = Arrays.stream(ConfigProperty.values());
        if (refreshHandler instanceof RequiresConfiguration) {
            propertyStream = Stream.concat(propertyStream,
                ((RequiresConfiguration) refreshHandler).getRequiredConfigProperties().stream());
        }
        return propertyStream.collect(Collectors.toSet());
    }


    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {
        boolean usesSharedToken = config.getConfigPropertyAsOptional(ConfigProperty.SHARED_TOKEN).map(Boolean::parseBoolean).orElse(false);

        AccessToken accessToken = null;
        if (usesSharedToken) {
            String accessTokenSerialized = config.getConfigPropertyAsOptional(ConfigProperty.WRITABLE_ACCESS_TOKEN).orElse(null);
            if (accessTokenSerialized != null) {
                try {
                    accessToken = SerializationUtils.deserialize(accessTokenSerialized.getBytes(StandardCharsets.UTF_8));
                } catch (SerializationException se) {
                    accessToken = null;
                }
            }
        }

        return OAuth2CredentialsWithRefresh.newBuilder()
            //TODO: pull an AccessToken from some cached location or something? otherwise will
            // be 'null' and refreshed for every request; and/or Keep credentials themselves in
            // memory
            .setAccessToken(accessToken)
            .setRefreshHandler(refreshHandler)
            .build();
    }

    public interface TokenRequestPayloadBuilder {

        String getGrantType();

        default boolean useSharedToken() {
            return false;
        }

        HttpContent buildPayload();

        /**
         * Add any headers to the request if needed, by default, does nothing
         * @param httpHeaders the request headers to modify
         */
        default void addHeaders(HttpHeaders httpHeaders) {}
    }

    @NoArgsConstructor(onConstructor_ = @Inject)
    @Log
    public static class TokenRefreshHandlerImpl implements OAuth2CredentialsWithRefresh.OAuth2RefreshHandler,
            RequiresConfiguration {

        @Inject
        ConfigService config;
        @Inject
        ObjectMapper objectMapper;
        @Inject
        HttpRequestFactory httpRequestFactory;
        @Inject
        OAuthRefreshTokenSourceAuthStrategy.TokenRequestPayloadBuilder payloadBuilder;

        @VisibleForTesting
        protected final Duration TOKEN_REFRESH_THRESHOLD = Duration.ofMinutes(1L);

        private AccessToken currentToken = null;

        /**
         * implements canonical oauth flow to exchange refreshToken for accessToken
         *
         * @return the resulting AccessToken
         * @throws IOException if anything went wrong;
         * @throws Error       if config values missing
         */
        @Override
        public AccessToken refreshAccessToken() throws IOException {
            CanonicalOAuthAccessTokenResponseDto tokenResponse;
            boolean isCurrentTokenValid = isCurrentTokenValid(this.currentToken, Instant.now());

            if (payloadBuilder.useSharedToken()) {
                // let's check if latest in store is newer than what we have locally cached
                Optional<AccessToken> sharedAccessToken = getSharedAccessToken();
                if (sharedAccessToken.isPresent() && this.currentToken != null) {
                    if (sharedAccessToken.get().getExpirationTime().after(this.currentToken.getExpirationTime())) {
                        // other instance refreshed, use that
                        this.currentToken = sharedAccessToken.get();
                        return this.currentToken;
                    }
                } else if (isCurrentTokenValid) {
                    // what we have should be good
                    return this.currentToken;
                }
                // refresh case
                tokenResponse = exchangeRefreshTokenForAccessToken();
                this.currentToken = asAccessToken(tokenResponse);
                storeSharedAccessToken(this.currentToken);
                return this.currentToken;
            } else {
                if (isCurrentTokenValid(this.currentToken, Instant.now())) {
                    return this.currentToken;
                }
                // usual case, exchange token always
                tokenResponse = exchangeRefreshTokenForAccessToken();
                this.currentToken = asAccessToken(tokenResponse);
                return this.currentToken;
            }
        }

        private CanonicalOAuthAccessTokenResponseDto exchangeRefreshTokenForAccessToken() throws IOException {
            String refreshEndpoint =
                config.getConfigPropertyOrError(ConfigProperty.REFRESH_ENDPOINT);

            HttpRequest tokenRequest = httpRequestFactory
                .buildPostRequest(new GenericUrl(refreshEndpoint), payloadBuilder.buildPayload());

            // modify any header if needed
            payloadBuilder.addHeaders(tokenRequest.getHeaders());

            HttpResponse response = tokenRequest.execute();
            CanonicalOAuthAccessTokenResponseDto tokenResponse =
                objectMapper.readerFor(CanonicalOAuthAccessTokenResponseDto.class)
                    .readValue(response.getContent());

            //TODO: this is obviously not great; if we're going to support refresh token rotation,
            // need to have some way to control the logic based on grant type
            config.getConfigPropertyAsOptional(RefreshTokenPayloadBuilder.ConfigProperty.REFRESH_TOKEN)
                .ifPresent(currentRefreshToken -> {
                    if (!Objects.equals(currentRefreshToken, tokenResponse.getRefreshToken())) {
                        //TODO: update refreshToken (some source APIs do this; TBC whether ones currently
                        // in scope for psoxy use do)
                        //q: write to secret? (most correct ...)
                        //q: write to file system?
                        log.severe("Source API rotated refreshToken, which is currently NOT supported by psoxy");
                    }
                });

            return tokenResponse;
        }


        AccessToken asAccessToken(CanonicalOAuthAccessTokenResponseDto tokenResponse) {
            //expires_in is RECOMMENDED, not REQUIRED in response; if omitted, we're supposed to
            // assume a default value for service OR retrieve via some other means
            Integer expiresIn = Optional.ofNullable(tokenResponse.getExpiresIn())
                .orElse((int) DEFAULT_ACCESS_TOKEN_EXPIRATION.toSeconds());
            return new AccessToken(tokenResponse.getAccessToken(),
                Date.from(Instant.now().plusSeconds(expiresIn)));
        }

        @VisibleForTesting
        protected boolean isCurrentTokenValid(AccessToken accessToken, Instant now) {
            if (accessToken == null) {
                return false;
            }
            Instant expiresAt = accessToken.getExpirationTime().toInstant();
            Instant minimumValid = expiresAt.minus(TOKEN_REFRESH_THRESHOLD);
            return now.isBefore(minimumValid);
        }

        @Override
        public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
            Stream<ConfigService.ConfigProperty> propertyStream = Arrays.stream(ConfigProperty.values());
            if (payloadBuilder instanceof RequiresConfiguration) {
                propertyStream = Stream.concat(propertyStream,
                    ((RequiresConfiguration) payloadBuilder).getRequiredConfigProperties().stream());
            }
            return propertyStream.collect(Collectors.toSet());
        }


        private Optional<AccessToken> getSharedAccessToken() {
            Preconditions.checkState(payloadBuilder.useSharedToken(), "Shared token not supported");
            Optional<String> jsonToken = config.getConfigPropertyAsOptional(ConfigProperty.WRITABLE_ACCESS_TOKEN);
            if (jsonToken.isEmpty()) {
                return Optional.empty();
            } else {
                try {
                    AccessTokenDto accessTokenDto = objectMapper.readerFor(AccessTokenDto.class).readValue(jsonToken.get().getBytes(StandardCharsets.UTF_8));
                    return Optional.ofNullable(accessTokenDto).map(AccessTokenDto::asAccessToken);
                } catch (IOException e) {
                    log.log(Level.SEVERE, "Could not parse contents of token into an object", e);
                    return Optional.empty();
                }
            }
        }

        private void storeSharedAccessToken(@NonNull AccessToken accessToken) {
            Preconditions.checkState(payloadBuilder.useSharedToken(), "Shared token not supported");
            try {
                config.putConfigProperty(ConfigProperty.WRITABLE_ACCESS_TOKEN,
                    objectMapper.writerFor(AccessTokenDto.class)
                        .writeValueAsString(AccessTokenDto.toAccessTokenDto(accessToken)));
                log.log(Level.INFO, "New token stored in config");
            } catch (JsonProcessingException e) {
                log.log(Level.SEVERE, "Could not write token into JSON", e);
            }
        }
    }


}
