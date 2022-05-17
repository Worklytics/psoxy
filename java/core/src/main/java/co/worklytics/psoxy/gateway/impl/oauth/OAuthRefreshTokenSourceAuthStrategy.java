package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * source auth strategy to authenticate using a short-lived OAuth 2.0 access token which must be
 * periodically refreshed.
 *   Options for refresh method are configured by
 *
 * A new access token will be retrieved for every psoxy instance that spins up; as well as when the
 * current one expires.  We'll endeavor to minimize the number of token requests by sharing this
 * states across API requests
 *
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
        return OAuth2CredentialsWithRefresh.newBuilder()
            //TODO: pull an AccessToken from some cached location or something? otherwise will
            // be 'null' and refreshed for every request; and/or Keep credentials themselves in
            // memory
            .setRefreshHandler(refreshHandler)
            .build();
    }

    public interface TokenRequestPayloadBuilder {

        String getGrantType();

        HttpContent buildPayload();
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
            if (isCurrentTokenValid(this.currentToken, Instant.now())) {
                return this.currentToken;
            }
            String refreshEndpoint =
                config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT);

            HttpRequest tokenRequest = httpRequestFactory
                .buildPostRequest(new GenericUrl(refreshEndpoint), payloadBuilder.buildPayload());

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

            this.currentToken = asAccessToken(tokenResponse);
            return this.currentToken;
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
    }


}
