package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.time.Instant;
import java.util.*;


/**
 * source auth strategy to authenticate by first using a long-lived OAuth 2.0 refresh token to
 * retrieve a short-lived OAuth 2.0 access token, and then using that access token to authenticate
 * against the source API.
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


    //q: should we put these as config properties? creates potential for inconsistent configs
    // eg, orphaned config properties for SourceAuthStrategy not in use; missing config properties
    //  expected by this
    enum ConfigProperty implements ConfigService.ConfigProperty {
        REFRESH_TOKEN, //NOTE: you should configure this as a secret in Secret Manager
        REFRESH_ENDPOINT,
        CLIENT_ID,
        CLIENT_SECRET, //NOTE: you should configure this as a secret in Secret Manager
    }

    @Inject Provider<OAuth2CredentialsWithRefresh.OAuth2RefreshHandler> refreshHandlerProvider;

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(ConfigProperty.values());
    }


    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {
        return OAuth2CredentialsWithRefresh.newBuilder()
            //TODO: pull an AccessToken from some cached location or something? otherwise will
            // be 'null' and refreshed for every request; and/or Keep credentials themselves in
            // memory
            .setRefreshHandler(refreshHandlerProvider.get())
            .build();
    }



    @NoArgsConstructor(onConstructor_ = @Inject)
    @Log
    public static class RefreshHandlerImpl implements OAuth2CredentialsWithRefresh.OAuth2RefreshHandler {

        @Inject ConfigService config;
        @Inject ObjectMapper objectMapper;
        @Inject HttpRequestFactory httpRequestFactory;


        /**
         * implements canonical oauth flow to exchange refreshToken for accessToken
         *
         * @return the resulting AccessToken
         * @throws IOException if anything went wrong;
         * @throws Error if config values missing
         */
        @Override
        public AccessToken refreshAccessToken() throws IOException {
            String refreshEndpoint = config.getConfigPropertyOrError(ConfigProperty.REFRESH_ENDPOINT);

            HttpRequest tokenRequest = httpRequestFactory
                .buildPostRequest(new GenericUrl(refreshEndpoint), tokenRequestPayload());

            HttpResponse response = tokenRequest.execute();

            response.getStatusCode();

            CanonicalOAuthAccessTokenResponseDto tokenResponse =
                objectMapper.readerFor(CanonicalOAuthAccessTokenResponseDto.class)
                    .readValue(response.getContent());

            String currentRefreshToken = config.getConfigPropertyOrError(ConfigProperty.REFRESH_TOKEN);
            if (!Objects.equals(currentRefreshToken, tokenResponse.getRefreshToken())) {
                //TODO: update refreshToken (some source APIs do this; TBC whether ones currently
                // in scope for psoxy use do)
                //q: write to secret? (most correct ...)
                //q: write to file system?
                log.severe("Source API rotated refreshToken, which is currently NOT supported by psoxy");
            }

            return asAccessToken(tokenResponse);
        }

        HttpContent tokenRequestPayload() {

            Map<String, String> data = new HashMap<>();

            data.put("grant_type", "refresh_token");
            data.put("refresh_token", config.getConfigPropertyOrError(ConfigProperty.REFRESH_TOKEN));
            data.put("client_id", config.getConfigPropertyOrError(ConfigProperty.CLIENT_ID));
            data.put("client_secret", config.getConfigPropertyOrError(ConfigProperty.CLIENT_SECRET));

            return new UrlEncodedContent(data);

        }



        AccessToken asAccessToken(CanonicalOAuthAccessTokenResponseDto tokenResponse) {
            return new AccessToken(tokenResponse.getAccessToken(),
                Date.from(Instant.now().plusSeconds(tokenResponse.getExpires())));
        }

        /**
         * something like:
         * {
         *   "access_token": "BWjcyMzY3ZDhiNmJkNTY",
         *   "refresh_token": "Srq2NjM5NzA2OWJjuE7c",
         *   "token_type": "bearer",
         *   "expires": 3600
         * }
         */
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        @JsonPropertyOrder(alphabetic = true) //for consistent tests
        @NoArgsConstructor //for jackson
        @Getter
        static class CanonicalOAuthAccessTokenResponseDto {

            String accessToken;
            String refreshToken;
            String tokenType;
            Integer expires;

            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            @Getter(onMethod_ = {@JsonAnyGetter})  //magically put 'unmapped' properties back onto JSON when serialized
            private Map<String, Object> unmapped = new TreeMap<>();  //treemap, to try to make order deterministic

            @JsonAnySetter
            public void set(String name, Object value) {
                unmapped.put(name, value);
            }
        }
    }

}
