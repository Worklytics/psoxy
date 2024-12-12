package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Uninterruptibles;
import lombok.*;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
     * Nov 23: lower this down to 1h
     */
    public static final Duration MIN_DURATION_TO_KEEP_REFRESH_TOKEN = Duration.ofHours(1);


    //q: should we put these as config properties? creates potential for inconsistent configs
    // eg, orphaned config properties for SourceAuthStrategy not in use; missing config properties
    //  expected by this
    @AllArgsConstructor
    @RequiredArgsConstructor
    public enum ConfigProperty implements ConfigService.ConfigProperty {
        REFRESH_ENDPOINT(false, false),
        CLIENT_ID(false, false),
        GRANT_TYPE(false, true),
        ACCESS_TOKEN(true, false),

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
         * TODO: rename to ACCESS_TOKEN_SINGULAR or "single active access token" or "strict token rotation"
         *
         * @return whether resulting `access_token` should be shared across all instances of
         * connections to this source.
         */
        USE_SHARED_TOKEN(false),

        //TODO: whether safe to cache access token or not
        ACCESS_TOKEN_CACHEABLE(false),


        TOKEN_RESPONSE_TYPE(false)
        ;

        private final Boolean noCache;

        @Override
        public Boolean noCache() {
            return noCache;
        }

        ;

        @Getter
        private boolean envVarOnly = true;
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
         * @return request paylaod for token request
         */
        HttpContent buildPayload();

        /**
         * Add any headers to the request if needed, by default, does nothing
         * @param httpHeaders the request headers to modify
         */
        default void addHeaders(HttpHeaders httpHeaders) {}
    }

    public interface TokenResponseParser {
        String getName();

        CanonicalOAuthAccessTokenResponseDto parseTokenResponse(HttpResponse response) throws IOException;
    }

    @Singleton
    @NoArgsConstructor(onConstructor_ = @Inject)
    @Log
    public static class TokenRefreshHandlerImpl implements OAuth2CredentialsWithRefresh.OAuth2RefreshHandler,
            RequiresConfiguration {

        @VisibleForTesting
        static final int WRITE_RETRIES = 3;

        @Inject
        ConfigService config;
        @Inject
        SecretStore secretStore;
        @Inject
        ObjectMapper objectMapper;
        @Inject
        HttpRequestFactory httpRequestFactory;
        @Inject
        TokenRequestBuilder payloadBuilder;
        @Inject
        Clock clock;
        @Inject //injected, so can be mocked for tests
        RandomNumberGenerator randomNumberGenerator;
        @Inject
        LockService lockService;
        @Inject
        TokenResponseParser tokenResponseParser;

        @VisibleForTesting
        protected final Duration MIN_PROACTIVE_TOKEN_REFRESH = Duration.ofMinutes(2L);
        @VisibleForTesting
        protected final Duration MAX_PROACTIVE_TOKEN_REFRESH = MIN_PROACTIVE_TOKEN_REFRESH.plusMinutes(5L);

        // not high; better to fail fast and leave it to the caller (Worklytics) to retry than hold
        // open a lambda waiting for a lock
        //NOTE: this is OAUTH_REFRESH_TOKEN secret / parameter that we expect to exist (created by Terraform modules in usual case)
        private static final String TOKEN_REFRESH_LOCK_ID = "OAUTH_REFRESH_TOKEN";

        private static final int MAX_TOKEN_REFRESH_ATTEMPTS = 3;

        /**
         * how long to allow for eventual consistency after write to config
         */
        private static final Duration ALLOWANCE_FOR_EVENTUAL_CONSISTENCY = Duration.ofSeconds(2);

        /**
         * how long to wait after a failed lock attempt before trying again; multiplier on the
         * attempt.
         *
         * goal is that this value should be big enough to allow the process that holds the lock to
         *  1) refresh the token it
         *  2) write it
         *  3) release the lock
         *  4) have that write be visible to other processes, given eventual consistency in GCP
         *     Secret Manager case
         *
         * this includes the allowance for eventual consistency, so wait should be >= that value
         * in practice.
         */
        private static final Duration WAIT_AFTER_FAILED_LOCK_ATTEMPTS = ALLOWANCE_FOR_EVENTUAL_CONSISTENCY.plusSeconds(2);

        /**
         * token lock duration; should be long enough to allow for token refresh + write to config
         */
        private static final Duration TOKEN_LOCK_DURATION = Duration.ofMinutes(2);

        private AccessToken cachedToken = null;

        /**
         * implements canonical oauth flow to exchange refreshToken for accessToken
         *
         * @return the resulting AccessToken
         * @throws IOException if anything went wrong;
         * @throws Error       if config values missing
         */
        @Override
        public synchronized AccessToken refreshAccessToken() throws IOException {
            return refreshAccessToken(0);
        }

        private boolean useSharedToken() {
            Optional<String> useSharedTokenConfig =
                config.getConfigPropertyAsOptional(ConfigProperty.USE_SHARED_TOKEN);

            //legacy behavior was that tokens shared in account_credentials grant type
            // see: https://github.com/Worklytics/psoxy/blob/v0.4.31/java/core/src/main/java/co/worklytics/psoxy/gateway/impl/oauth/AccountCredentialsGrantTokenRequestBuilder.java#L55-L57
            // and Zoom was the only source that used account_credentials grant type at the time
            boolean isClientCredentialsGrantType =
                config.getConfigPropertyAsOptional(ConfigProperty.GRANT_TYPE)
                    .map(AccountCredentialsGrantTokenRequestBuilder.GRANT_TYPE::equals)
                    .orElse(false);

            return useSharedTokenConfig.map(Boolean::parseBoolean).orElse(isClientCredentialsGrantType);
        }

        private boolean isAccessTokenCacheable() {
            return
                config.getConfigPropertyAsOptional(ConfigProperty.ACCESS_TOKEN_CACHEABLE)
                .map(Boolean::parseBoolean)
                .orElse(!useSharedToken()); // by default, tokens cacheable unless shared
        }

        private AccessToken refreshAccessToken(int attempt) throws IOException {
            if (attempt == MAX_TOKEN_REFRESH_ATTEMPTS) {
                throw new RuntimeException("Failed to refresh token after " + attempt + " attempts");
            }

            CanonicalOAuthAccessTokenResponseDto tokenResponse;

            AccessToken token = getSharedAccessTokenIfSupported().orElse(this.cachedToken);

            if (shouldRefresh(token, clock.instant())) {

                // only lock if we're using a shared token across processes
                boolean lockNeeded = useSharedToken();

                boolean acquired = !lockNeeded || lockService.acquire(TOKEN_REFRESH_LOCK_ID, TOKEN_LOCK_DURATION);

                if (acquired) {
                    tokenResponse = exchangeRefreshTokenForAccessToken();
                    token = asAccessToken(tokenResponse);

                    storeSharedAccessTokenIfSupported(token, lockNeeded);
                    storeRefreshTokenIfRotated(tokenResponse);

                    if (isAccessTokenCacheable()) {
                        this.cachedToken = token;
                    }

                    if (lockNeeded) {
                        // hold lock extra, to try to maximize the time between token refreshes
                        Uninterruptibles.sleepUninterruptibly(ALLOWANCE_FOR_EVENTUAL_CONSISTENCY);
                        lockService.release(TOKEN_REFRESH_LOCK_ID);
                    }
                } else {
                    //re-try recursively, w/ linear backoff
                    Uninterruptibles.sleepUninterruptibly(WAIT_AFTER_FAILED_LOCK_ATTEMPTS
                        .plusMillis(randomNumberGenerator.nextInt(250))
                        .multipliedBy(attempt + 1));

                    token = refreshAccessToken(attempt + 1);
                }
            }

            return token;
        }


        private CanonicalOAuthAccessTokenResponseDto exchangeRefreshTokenForAccessToken() throws IOException {
            String refreshEndpoint =
                config.getConfigPropertyOrError(ConfigProperty.REFRESH_ENDPOINT);

            HttpRequest tokenRequest = httpRequestFactory
                .buildPostRequest(new GenericUrl(refreshEndpoint), payloadBuilder.buildPayload());

            // modify any header if needed
            payloadBuilder.addHeaders(tokenRequest.getHeaders());

            HttpResponse response = tokenRequest.execute();

            return tokenResponseParser.parseTokenResponse(response);
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
                secretStore.getConfigPropertyWithMetadata(RefreshTokenTokenRequestBuilder.ConfigProperty.REFRESH_TOKEN)
                    .filter(storedToken -> !Objects.equals(storedToken.getValue(), tokenResponse.getRefreshToken()))
                    .filter(storedToken -> storedToken.getLastModifiedDate().isEmpty()
                        || storedToken.getLastModifiedDate().get()
                                .isBefore(Instant.now().minus(MIN_DURATION_TO_KEEP_REFRESH_TOKEN)))
                    .ifPresent(storedTokenToRotate -> {
                        // if reaching here, there's a new refresh token AND stored token was last written at least MIN_DURATION_TO_KEEP_REFRESH_TOKEN ago
                        // (want to avoid churning through refresh tokens if source is giving us a new one every time, as this is pretty expensive for secret manager)
                        try {
                            log.info("New oauth refresh_token came with access_token response; updating stored value");
                            secretStore.putConfigProperty(RefreshTokenTokenRequestBuilder.ConfigProperty.REFRESH_TOKEN,
                                    tokenResponse.getRefreshToken(), WRITE_RETRIES);
                        } catch (WritePropertyRetriesExhaustedException e) {
                            log.log(Level.SEVERE, "refresh_token rotated, but failed to write updated value after " + WRITE_RETRIES + " attempts; while this access_token may work, future token exchanges may fail", e);
                        } catch (Throwable e) {
                            log.log(Level.SEVERE, "refresh_token rotated, but failed to write updated value; while this access_token may work, future token exchanges may fail", e);
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


        /**
         * whether token should be refreshed
         *   - it's null
         *   - it's expired
         *   - it's close to expiring (proactive refresh)
         *
         * @param accessToken to check
         * @param now effective time of check
         * @return whether token should be refreshed
         */

        @VisibleForTesting
        protected boolean shouldRefresh(AccessToken accessToken, Instant now) {
            if (accessToken == null) {
                return true;
            }
            Instant expiresAt = accessToken.getExpirationTime().toInstant();
            Instant thresholdToProactiveRefresh = expiresAt.minusSeconds(getProactiveGracePeriodSeconds());
            return now.isAfter(thresholdToProactiveRefresh);
        }

        @VisibleForTesting
        protected int getProactiveGracePeriodSeconds() {
            int maxSeconds = (int) MAX_PROACTIVE_TOKEN_REFRESH.toSeconds();
            int minSeconds = (int) MIN_PROACTIVE_TOKEN_REFRESH.toSeconds();
            return randomNumberGenerator.nextInt(maxSeconds - minSeconds) + minSeconds;
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
            if (useSharedToken()) {
                Optional<String> jsonToken = secretStore.getConfigPropertyAsOptional(ConfigProperty.ACCESS_TOKEN);
                if (jsonToken.isEmpty()) {
                    return Optional.empty();
                } else {
                    byte[] value = jsonToken.get().getBytes(StandardCharsets.UTF_8);
                    try {
                        AccessTokenDto accessTokenDto = objectMapper.readerFor(AccessTokenDto.class).readValue(value);
                        return Optional.ofNullable(accessTokenDto).map(AccessTokenDto::asAccessToken);
                    } catch (IOException e) {
                        //NOTE: not logging 'e' itself, as sometimes includes value, so if value
                        // really is a proper token then we don't want it in the logs
                        log.log(Level.WARNING, "Could not parse contents of token into an AccessToken object; possibly expected initially, if config has a placeholder value for token");
                        return Optional.empty();
                    }
                }
            } else {
                return Optional.empty();
            }
        }

        @VisibleForTesting
        void storeSharedAccessTokenIfSupported(@NonNull AccessToken accessToken, boolean useSharedToken) {
            if (useSharedToken) {
                try {
                    secretStore.putConfigProperty(ConfigProperty.ACCESS_TOKEN,
                        objectMapper.writerFor(AccessTokenDto.class)
                            .writeValueAsString(AccessTokenDto.toAccessTokenDto(accessToken)), WRITE_RETRIES);
                    log.log(Level.INFO, "New token stored in config");
                } catch (JsonProcessingException e) {
                    log.log(Level.SEVERE, "Could not serialize token into JSON", e);
                } catch (WritePropertyRetriesExhaustedException e) {
                    log.log(Level.SEVERE, "Could not write access token to config after " + WRITE_RETRIES + " attempts", e);
                }
            }
        }
    }

    @NoArgsConstructor(onConstructor_ = @Inject)
    public static class TokenResponseParserImpl implements TokenResponseParser {
        @Inject
        ObjectMapper objectMapper;

        @Override
        public String getName() {
            return "DEFAULT";
        }

        @Override
        public CanonicalOAuthAccessTokenResponseDto parseTokenResponse(HttpResponse response) throws IOException {
            return objectMapper.readerFor(CanonicalOAuthAccessTokenResponseDto.class)
                    .readValue(response.getContent());
        }
    }


}
