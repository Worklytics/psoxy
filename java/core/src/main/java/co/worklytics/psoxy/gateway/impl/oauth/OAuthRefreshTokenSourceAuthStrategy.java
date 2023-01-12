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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    /**
     * some sources seem to give you a new refresh token on EVERY token refresh request; we don't
     * want to churn through refresh tokens when not really needed
     *
     * examples: Dropbox
     *
     * TODO: revisit whether this needs to be configured per data source
     */
    public static final Duration MIN_DURATION_TO_KEEP_REFRESH_TOKEN = Duration.ofDays(7);


    //q: should we put these as config properties? creates potential for inconsistent configs
    // eg, orphaned config properties for SourceAuthStrategy not in use; missing config properties
    //  expected by this
    public enum ConfigProperty implements ConfigService.ConfigProperty {
        REFRESH_ENDPOINT,
        CLIENT_ID,
        GRANT_TYPE,
        ACCESS_TOKEN,
    }

    @Inject OAuth2CredentialsWithRefresh.OAuth2RefreshHandler refreshHandler;

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        Stream<ConfigService.ConfigProperty> propertyStream = Stream.empty();

        if (refreshHandler instanceof RequiresConfiguration) {
            propertyStream = Stream.concat(propertyStream,
                ((RequiresConfiguration) refreshHandler).getRequiredConfigProperties().stream());
        }
        return propertyStream.collect(Collectors.toSet());
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        Stream<ConfigService.ConfigProperty> propertyStream = Stream.of(ConfigProperty.values());

        if (refreshHandler instanceof RequiresConfiguration) {
            propertyStream = Stream.concat(propertyStream,
                ((RequiresConfiguration) refreshHandler).getAllConfigProperties().stream());
        }
        return propertyStream.collect(Collectors.toSet());
    }


    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {
        return OAuth2CredentialsWithRefresh.newBuilder()
            //TODO: pull an AccessToken from some cached location or something? otherwise will
            // be 'null' and refreshed for every request; and/or Keep credentials themselves in
            // memory
            .setRefreshHandler(refreshHandler)
            .build();
    }

    public interface TokenRequestBuilder {

        /**
         * @return identifier of type of OAuth grant that this payload builder should be used for
         */
        String getGrantType();

        /**
         * whether resulting `access_token` should be shared across all instances of connections
         * to this source.
         *
         * q: what does this have to do with a token request payload?? it's semantics of tokens
         * according to Source, right? (eg, whether they allow multiple valid token instances to
         * be used concurrently for the same grant)
         *
         * q: maybe this should just *always* be true? or should be env var?
         *
         * @return whether resulting `access_token` should be shared across all instances of
         * connections to this source.
         */
        default boolean useSharedToken() {
            return false;
        }

        /**
         * @return request paylaod for token request
         */
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
        TokenRequestBuilder payloadBuilder;
        @Inject
        Clock clock;

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

            Optional<AccessToken> sharedAccessToken = getSharedAccessTokenIfSupported();
            if (this.currentToken == null) {
                this.currentToken = sharedAccessToken.orElse(null);
            }

            if (sharedAccessToken.isPresent()) {
                // we have a token, but shared token is newer. Other instance refreshed, so use it
                if (sharedAccessToken.get().getExpirationTime().after(this.currentToken.getExpirationTime())) {
                    this.currentToken = sharedAccessToken.get();
                }
            }

            if (isCurrentTokenValid(this.currentToken, clock.instant())) {
                return this.currentToken;
            }

            tokenResponse = exchangeRefreshTokenForAccessToken();
            this.currentToken = asAccessToken(tokenResponse);
            storeSharedAccessTokenIfSupported(this.currentToken);
            return this.currentToken;
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

            storeRefreshTokenIfRotated(tokenResponse);

            return tokenResponse;
        }

        /**
         * Store the new refresh token if included in token response and differs from stored value
         *
         * this method encapsulates the logic for checking and storing new token
         *
         * @param tokenResponse the token response
         */
        @VisibleForTesting
        void storeRefreshTokenIfRotated(CanonicalOAuthAccessTokenResponseDto tokenResponse) {
            if (!StringUtils.isBlank(tokenResponse.getRefreshToken())) {
                //if a refresh_token came back from server, potentially update it
                config.getConfigPropertyWithMetadata(RefreshTokenBuilder.ConfigProperty.REFRESH_TOKEN)
                    .filter(storedToken -> !Objects.equals(storedToken.getValue(), tokenResponse.getRefreshToken()))
                    .filter(storedToken -> storedToken.getLastModifiedDate().isEmpty()
                        || storedToken.getLastModifiedDate().get()
                                .isBefore(Instant.now().minus(MIN_DURATION_TO_KEEP_REFRESH_TOKEN)))
                    .ifPresent(storedTokenToRotate -> {
                        if (config.supportsWriting()) {
                            try {
                                config.putConfigProperty(RefreshTokenBuilder.ConfigProperty.REFRESH_TOKEN,
                                    tokenResponse.getRefreshToken());
                            } catch (Throwable e) {
                                log.log(Level.SEVERE, "refresh_token rotated, but failed to write updated value; while this access_token may work, future token exchanges may fail", e);
                            }
                        } else {
                            log.log(Level.SEVERE, "refresh_token rotated, but config service does not support writing; while this access_token may work, future token exchanges may fail");
                        }
                    });
            }
        }


        AccessToken asAccessToken(CanonicalOAuthAccessTokenResponseDto tokenResponse) {
            //expires_in is RECOMMENDED, not REQUIRED in response; if omitted, we're supposed to
            // assume a default value for service OR retrieve via some other means
            Integer expiresIn = Optional.ofNullable(tokenResponse.getExpiresIn())
                .orElse((int) DEFAULT_ACCESS_TOKEN_EXPIRATION.toSeconds());
            return new AccessToken(tokenResponse.getAccessToken(),
                Date.from(clock.instant().plusSeconds(expiresIn)));
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

            // only things
            Stream<ConfigService.ConfigProperty> propertyStream = Stream.of(
                    ConfigProperty.REFRESH_ENDPOINT,
                    // ACCESS_TOKEN is optional
                    ConfigProperty.GRANT_TYPE
                      // CLIENT_ID not required by RefreshHandler, though likely by payload builder
            );

            if (payloadBuilder instanceof RequiresConfiguration) {
                propertyStream = Stream.concat(propertyStream,
                    ((RequiresConfiguration) payloadBuilder).getRequiredConfigProperties().stream());
            }
            return propertyStream.collect(Collectors.toSet());
        }

        @Override
        public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
            Stream<ConfigService.ConfigProperty> allConfigPropertiesStream = Arrays.stream(ConfigProperty.values());

            if (payloadBuilder instanceof RequiresConfiguration) {
                allConfigPropertiesStream = Stream.concat(allConfigPropertiesStream,
                    ((RequiresConfiguration) payloadBuilder).getAllConfigProperties().stream());
            }
            return allConfigPropertiesStream.collect(Collectors.toSet());
        }

        @VisibleForTesting
        Optional<AccessToken> getSharedAccessTokenIfSupported() {
            if (payloadBuilder.useSharedToken()) {
                Optional<String> jsonToken = config.getConfigPropertyAsOptional(ConfigProperty.ACCESS_TOKEN);
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
            } else {
                return Optional.empty();
            }
        }

        @VisibleForTesting
        void storeSharedAccessTokenIfSupported(@NonNull AccessToken accessToken) {
            if (payloadBuilder.useSharedToken()) {
                try {
                    config.putConfigProperty(ConfigProperty.ACCESS_TOKEN,
                        objectMapper.writerFor(AccessTokenDto.class)
                            .writeValueAsString(AccessTokenDto.toAccessTokenDto(accessToken)));
                    log.log(Level.INFO, "New token stored in config");
                } catch (JsonProcessingException e) {
                    log.log(Level.SEVERE, "Could not serialize token into JSON", e);
                }
            }
        }
    }


}
