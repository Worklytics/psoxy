package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ConfigService.ConfigValueVersion;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.WritePropertyRetriesExhaustedException;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.utils.DevLogUtils;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Uninterruptibles;
import dagger.Lazy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * source auth strategy to authenticate using a short-lived OAuth 2.0 access token which must be
 * periodically refreshed. Options for refresh method are configured by
 * <p>
 * A new access token will be retrieved for every psoxy instance that spins up; as well as when the
 * current one expires. We'll endeavor to minimize the number of token requests by sharing this
 * states across API requests
 * <p>
 * If the source API you're connecting to offers long-lived access tokens (or does not offer refresh
 * tokens), you may opt for the access-token only strategy:
 *
 * @see OAuthAccessTokenSourceAuthStrategy
 *
 */
@Singleton
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
     * TODO: revisit whether this needs to be configured per data source Nov 23: lower this down to
     * 1h
     */
    public static final Duration MIN_DURATION_TO_KEEP_REFRESH_TOKEN = Duration.ofHours(1);


    // so we'll attempt to *proactively* refresh the token 1-9 minutes before it expires
    @VisibleForTesting
    protected final Duration MIN_PROACTIVE_TOKEN_REFRESH = Duration.ofMinutes(1L);
    @VisibleForTesting
    protected final Duration MAX_PROACTIVE_TOKEN_REFRESH =
            MIN_PROACTIVE_TOKEN_REFRESH.plusMinutes(9L);

    // q: should we put these as config properties? creates potential for inconsistent configs
    // eg, orphaned config properties for SourceAuthStrategy not in use; missing config properties
    // expected by this
    @AllArgsConstructor
    @RequiredArgsConstructor
    public enum ConfigProperty implements ConfigService.ConfigProperty {
        REFRESH_ENDPOINT(false, SupportedSource.ENV_VAR_OR_REMOTE),
        CLIENT_ID(false, SupportedSource.ENV_VAR_OR_REMOTE),
        GRANT_TYPE(false, SupportedSource.ENV_VAR),
        ACCESS_TOKEN(true, SupportedSource.ENV_VAR_OR_REMOTE),

        /**
         * whether resulting `access_token` should be shared across all instances of connections to
         * this source.
         *
         * q: what does this have to do with a token request payload?? it's semantics of tokens
         * according to Source, right? (eg, whether they allow multiple valid token instances to be
         * used concurrently for the same grant)
         *
         * q: maybe this should just *always* be true? or should be env var?
         *
         * TODO: rename to ACCESS_TOKEN_SINGULAR or "single active access token" or "strict token
         * rotation"
         *
         * @return whether resulting `access_token` should be shared across all instances of
         *         connections to this source.
         */
        USE_SHARED_TOKEN(false, SupportedSource.ENV_VAR),

        // TODO: whether safe to cache access token or not
        ACCESS_TOKEN_CACHEABLE(false, SupportedSource.ENV_VAR),


        TOKEN_RESPONSE_TYPE(false, SupportedSource.ENV_VAR);

        private final Boolean noCache;

        @Override
        public Boolean noCache() {
            return noCache;
        }

        @Getter(onMethod_ = @Override)
        private SupportedSource supportedSource = SupportedSource.ENV_VAR;
    }

    @Inject
    ConfigService config;
    @Inject
    SecretStore secretStore;
    @Inject
    Clock clock;
    @Inject
    Lazy<OAuth2CredentialsWithRefresh.OAuth2RefreshHandler> refreshHandlerProvider;
    @Inject
    ObjectMapper objectMapper;
    @Inject // injected, so can be mocked for tests
    RandomNumberGenerator randomNumberGenerator;

    /**
     * -- SETTER --
     *  Sets the local copy of the token, assumes valid until expiration.
     *
     * @param token
     */
    @Setter
    @Getter
    private AccessToken cachedToken = null;


    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        Stream<ConfigService.ConfigProperty> propertyStream = Stream.empty();

        if (getRefreshHandler() instanceof RequiresConfiguration) {
            propertyStream =
                    Stream.concat(propertyStream, ((RequiresConfiguration) getRefreshHandler())
                            .getRequiredConfigProperties().stream());
        }
        return propertyStream.collect(Collectors.toSet());
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        Stream<ConfigService.ConfigProperty> propertyStream = Stream.of(ConfigProperty.values());

        if (getRefreshHandler() instanceof RequiresConfiguration) {
            propertyStream =
                    Stream.concat(propertyStream, ((RequiresConfiguration) getRefreshHandler())
                            .getAllConfigProperties().stream());
        }
        return propertyStream.collect(Collectors.toSet());
    }

    private OAuth2CredentialsWithRefresh.OAuth2RefreshHandler getRefreshHandler() {
        return refreshHandlerProvider.get();
    }


    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {
        if (userToImpersonate.isPresent()) {
            log.warning("OAuthRefreshTokenSourceAuthStrategy does not support impersonation");
        }



        AccessToken accessToken = this.getCachedToken();
        if (accessToken == null) {
            accessToken = getSharedAccessTokenIfSupported().orElse(null);
            this.setCachedToken(accessToken);
        }


        if (shouldRefresh(accessToken, clock.instant())) {
            try {
                accessToken = getRefreshHandler().refreshAccessToken();
                this.setCachedToken(accessToken);
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to proactively refresh token", e);
            }
        }

       return OAuth2CredentialsWithRefresh.newBuilder()
           .setRefreshHandler(getRefreshHandler())
           .setAccessToken(accessToken)
           .build();
    }

    boolean useSharedToken() {
        Optional<String> useSharedTokenConfig =
                config.getConfigPropertyAsOptional(ConfigProperty.USE_SHARED_TOKEN);

        // legacy behavior was that tokens shared in account_credentials grant type
        // see:
        // https://github.com/Worklytics/psoxy/blob/v0.4.31/java/core/src/main/java/co/worklytics/psoxy/gateway/impl/oauth/AccountCredentialsGrantTokenRequestBuilder.java#L55-L57
        // and Zoom was the only source that used account_credentials grant type at the time
        boolean isClientCredentialsGrantType = config
                .getConfigPropertyAsOptional(ConfigProperty.GRANT_TYPE)
                .map(AccountCredentialsGrantTokenRequestBuilder.GRANT_TYPE::equals).orElse(false);

        return useSharedTokenConfig.map(Boolean::parseBoolean).orElse(isClientCredentialsGrantType);
    }

    @VisibleForTesting
    Optional<AccessToken> getSharedAccessTokenIfSupported() {
        if (useSharedToken()) {
            List<ConfigValueVersion> possibleTokens =
                secretStore.getAvailableVersions(ConfigProperty.ACCESS_TOKEN, 5);

            ObjectReader objectReader = objectMapper.readerFor(AccessTokenDto.class);
            // sort by expiration date, then by version
            // NOTE: oauth spec is for `expires_in`, which is a value in seconds ... this is parsed to Date by Google's lib
            // so various potential issues 1) quick refreshes could result in 2 tokens with same expiration date
            // 2) we don't know, or at least don't control, how google lib converts offset to date; nor is it clear what the right
            // approach even its; probably relative to a Date value in the http response header ... but we can't be certain - is that
            // when server SENT the response? began generating the response? what??
            // so YMMV
            return possibleTokens.stream()
                .map(value -> {
                    try {
                        AccessTokenDto dto = objectReader.readValue(value.getValue());
                        return Pair.of(value, dto);
                    } catch (IOException e) {
                        log.log(Level.WARNING, "Failed to parse stored access token JSON", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .min(Comparator
                    .comparing(
                        (Pair<ConfigValueVersion, AccessTokenDto> p) ->
                            p.getRight().getExpirationDate(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(
                        p -> p.getLeft().getVersion(),
                        Comparator.reverseOrder()))
                .map(Pair::getRight)
                .map(AccessTokenDto::asAccessToken);
        } else {
            return Optional.empty();
        }
    }

    /**
     * whether token should be refreshed - it's null - it's expired - it's close to expiring
     * (proactive refresh)
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
        Instant thresholdToProactiveRefresh =
                expiresAt.minusSeconds(getProactiveGracePeriodSeconds());
        return now.isAfter(thresholdToProactiveRefresh);
    }

    @VisibleForTesting
    protected int getProactiveGracePeriodSeconds() {
        int maxSeconds = (int) MAX_PROACTIVE_TOKEN_REFRESH.toSeconds();
        int minSeconds = (int) MIN_PROACTIVE_TOKEN_REFRESH.toSeconds();
        return randomNumberGenerator.nextInt(maxSeconds - minSeconds) + minSeconds;
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
         *
         * @param httpHeaders the request headers to modify
         */
        default void addHeaders(HttpHeaders httpHeaders) {}
    }

    public interface TokenResponseParser {
        String getName();

        CanonicalOAuthAccessTokenResponseDto parseTokenResponse(HttpResponse response)
                throws IOException;
    }

    // TODO: big enough to move to its own file
    @Singleton
    @NoArgsConstructor(onConstructor_ = @Inject)
    @Log
    public static class TokenRefreshHandlerImpl
            implements OAuth2CredentialsWithRefresh.OAuth2RefreshHandler, RequiresConfiguration {

        @VisibleForTesting
        static final int WRITE_RETRIES = 3;

        @Inject
        ConfigService config;
        @Inject
        EnvVarsConfigService envVarsConfigService;
        @Inject
        SecretStore secretStore;
        @Inject
        OAuthRefreshTokenSourceAuthStrategy sourceAuthStrategy;
        @Inject
        ObjectMapper objectMapper;
        @Inject
        HttpRequestFactory httpRequestFactory;
        @Inject
        TokenRequestBuilder payloadBuilder;
        @Inject
        Clock clock;
        @Inject
        LockService lockService;
        @Inject
        TokenResponseParser tokenResponseParser;

        @Inject // injected, so can be mocked for tests
        RandomNumberGenerator randomNumberGenerator;



        // not high; better to fail fast and leave it to the caller (Worklytics) to retry than hold
        // open a lambda waiting for a lock
        // NOTE: this is OAUTH_REFRESH_TOKEN secret / parameter that we expect to exist (created by
        // Terraform modules in usual case)
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
         * 1) refresh the token it 2) write it 3) release the lock 4) have that write be visible to
         * other processes, given eventual consistency in GCP Secret Manager case
         *
         * this includes the allowance for eventual consistency, so wait should be >= that value in
         * practice.
         */
        private static final Duration WAIT_AFTER_FAILED_LOCK_ATTEMPTS =
                ALLOWANCE_FOR_EVENTUAL_CONSISTENCY.plusSeconds(2);

        /**
         * token lock duration; should be long enough to allow for token refresh + write to config
         */
        private static final Duration TOKEN_LOCK_DURATION = Duration.ofMinutes(2);


        /**
         * implements canonical oauth flow to exchange refreshToken for accessToken
         *
         * @return the resulting AccessToken
         * @throws IOException if anything went wrong;
         * @throws Error if config values missing
         */
        @Override
        public synchronized AccessToken refreshAccessToken() throws IOException {
            if (sourceAuthStrategy.getCachedToken() != null) {
                DevLogUtils.info(envVarsConfigService, log, "Refreshing token, expires: " + sourceAuthStrategy.getCachedToken().getExpirationTime());
            } else {
                DevLogUtils.info(envVarsConfigService, log, "Refreshing token, no cached value yet");
            }
            return refreshAccessToken(0);
        }

        /**
         * On shared-token scenarios, check if the token has been refreshed on another instance
         * before attempting to refresh it locally.
         * @return
         */
        private Optional<AccessToken> checkIfAlreadyRefreshed() {
            AccessToken freshToken = sourceAuthStrategy.getSharedAccessTokenIfSupported().orElse(null);
            if (sourceAuthStrategy.shouldRefresh(freshToken, clock.instant())) {
                return Optional.empty();
            } else if (freshToken != null) {
                DevLogUtils.info(envVarsConfigService, log, "Token already refreshed " + freshToken.getExpirationTime());
                return Optional.of(freshToken);
            }
            return Optional.empty();
        }

        private AccessToken refreshAccessToken(int attempt) throws IOException {
            if (attempt == MAX_TOKEN_REFRESH_ATTEMPTS) {
                throw new RuntimeException("Failed to refresh token after " + attempt + " attempts");
            }

            // only lock if we're using a shared token across processes
            boolean lockNeeded = sourceAuthStrategy.useSharedToken();

            boolean acquired =
                !lockNeeded || lockService.acquire(TOKEN_REFRESH_LOCK_ID, TOKEN_LOCK_DURATION);

            AccessToken token;
            if (acquired) {
                DevLogUtils.info(envVarsConfigService, log, "Acquired lock to refresh token");
                Optional<AccessToken> refreshedToken = checkIfAlreadyRefreshed();
                if (refreshedToken.isEmpty()) {
                    // token still expired, refresh with lock acquired
                    DevLogUtils.info(envVarsConfigService, log, "Token refresh in progress");

                    CanonicalOAuthAccessTokenResponseDto tokenResponse = exchangeRefreshTokenForAccessToken();
                    token = asAccessToken(tokenResponse);

                    if (sourceAuthStrategy.useSharedToken()) {
                        storeSharedAccessTokenIfSupported(token);
                    }
                    // TODO: breaks abstraction?? whether there *is* a refresh token at all depends on
                    // the PayloadBuilder
                    storeRefreshTokenIfRotated(tokenResponse);

                } else {
                    DevLogUtils.info(envVarsConfigService, log, "Token already refreshed");
                    token = refreshedToken.get();
                }

                sourceAuthStrategy.setCachedToken(token);


                if (lockNeeded) {
                    // hold lock extra, to try to maximize the time between token refreshes
                    Uninterruptibles.sleepUninterruptibly(ALLOWANCE_FOR_EVENTUAL_CONSISTENCY);
                    lockService.release(TOKEN_REFRESH_LOCK_ID);
                    DevLogUtils.info(envVarsConfigService, log, "Released lock to refresh token");
                }
            } else {
                // re-try recursively, w/ linear backoff
                // this exec path should only happen when different instances (VMs) are attempting
                // to refresh. 2+ threads of same VM can never hit this as the whole recursive call
                // is synchronized (see #refreshAccessToken). So on same VM, this will block until
                // A) acquires lock and refreshes
                // B) checks already refreshed token and exits
                Uninterruptibles.sleepUninterruptibly(WAIT_AFTER_FAILED_LOCK_ATTEMPTS
                        .plusMillis(randomNumberGenerator.nextInt(250)).multipliedBy(attempt + 1));

                DevLogUtils.info(envVarsConfigService, log, "Failed to acquire lock to refresh token, re-trying. Attempt %d", attempt);
                token = refreshAccessToken(attempt + 1);
            }

            return token;
        }


        private CanonicalOAuthAccessTokenResponseDto exchangeRefreshTokenForAccessToken()
                throws IOException {
            String refreshEndpoint =
                    config.getConfigPropertyOrError(ConfigProperty.REFRESH_ENDPOINT);

            HttpRequest tokenRequest = httpRequestFactory.buildPostRequest(
                    new GenericUrl(refreshEndpoint), payloadBuilder.buildPayload());

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
                // if a refresh_token came back from server, potentially update it
                secretStore
                        .getConfigPropertyWithMetadata(
                                RefreshTokenTokenRequestBuilder.ConfigProperty.REFRESH_TOKEN)
                        .filter(storedToken -> !Objects.equals(storedToken.getValue(),
                                tokenResponse.getRefreshToken()))
                        .filter(storedToken -> storedToken.getLastModifiedDate().isEmpty()
                                || storedToken.getLastModifiedDate().get().isBefore(
                                        Instant.now().minus(MIN_DURATION_TO_KEEP_REFRESH_TOKEN)))
                        .ifPresent(storedTokenToRotate -> {
                            // if reaching here, there's a new refresh token AND stored token was
                            // last written at least MIN_DURATION_TO_KEEP_REFRESH_TOKEN ago
                            // (want to avoid churning through refresh tokens if source is giving us
                            // a new one every time, as this is pretty expensive for secret manager)
                            try {
                                log.info(
                                        "New oauth refresh_token came with access_token response; updating stored value");
                                secretStore.putConfigProperty(
                                        RefreshTokenTokenRequestBuilder.ConfigProperty.REFRESH_TOKEN,
                                        tokenResponse.getRefreshToken(), WRITE_RETRIES);
                            } catch (WritePropertyRetriesExhaustedException e) {
                                log.log(Level.SEVERE,
                                        "refresh_token rotated, but failed to write updated value after "
                                                + WRITE_RETRIES
                                                + " attempts; while this access_token may work, future token exchanges may fail",
                                        e);
                            } catch (Throwable e) {
                                log.log(Level.SEVERE,
                                        "refresh_token rotated, but failed to write updated value; while this access_token may work, future token exchanges may fail",
                                        e);
                            }
                        });
            }
        }


        AccessToken asAccessToken(CanonicalOAuthAccessTokenResponseDto tokenResponse) {
            // expires_in is RECOMMENDED, not REQUIRED in response; if omitted, we're supposed to
            // assume a default value for service OR retrieve via some other means
            Integer expiresIn = Optional.ofNullable(tokenResponse.getExpiresIn())
                    .orElse((int) DEFAULT_ACCESS_TOKEN_EXPIRATION.toSeconds());
            return new AccessToken(tokenResponse.getAccessToken(),
                    Date.from(clock.instant().plusSeconds(expiresIn)));
        }



        @Override
        public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {

            // only things
            Stream<ConfigService.ConfigProperty> propertyStream =
                    Stream.of(ConfigProperty.REFRESH_ENDPOINT,
                            // ACCESS_TOKEN is optional
                            ConfigProperty.GRANT_TYPE
                    // CLIENT_ID not required by RefreshHandler, though likely by payload builder
                    );

            if (payloadBuilder instanceof RequiresConfiguration) {
                propertyStream =
                        Stream.concat(propertyStream, ((RequiresConfiguration) payloadBuilder)
                                .getRequiredConfigProperties().stream());
            }
            return propertyStream.collect(Collectors.toSet());
        }

        @Override
        public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
            Stream<ConfigService.ConfigProperty> allConfigPropertiesStream =
                    Arrays.stream(ConfigProperty.values());

            if (payloadBuilder instanceof RequiresConfiguration) {
                allConfigPropertiesStream = Stream.concat(allConfigPropertiesStream,
                        ((RequiresConfiguration) payloadBuilder).getAllConfigProperties().stream());
            }
            return allConfigPropertiesStream.collect(Collectors.toSet());
        }


        @VisibleForTesting
        void storeSharedAccessTokenIfSupported(@NonNull AccessToken accessToken) {
            try {
                secretStore.putConfigProperty(
                        ConfigProperty.ACCESS_TOKEN, objectMapper.writerFor(AccessTokenDto.class)
                                .writeValueAsString(AccessTokenDto.toAccessTokenDto(accessToken)),
                        WRITE_RETRIES);
                log.log(Level.INFO, "New token stored in config");
            } catch (JsonProcessingException e) {
                log.log(Level.SEVERE, "Could not serialize token into JSON", e);
            } catch (WritePropertyRetriesExhaustedException e) {
                log.log(Level.SEVERE, "Could not write access token to config after "
                        + WRITE_RETRIES + " attempts", e);
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
        public CanonicalOAuthAccessTokenResponseDto parseTokenResponse(HttpResponse response)
                throws IOException {
            return objectMapper.readerFor(CanonicalOAuthAccessTokenResponseDto.class)
                    .readValue(response.getContent());
        }
    }



}
