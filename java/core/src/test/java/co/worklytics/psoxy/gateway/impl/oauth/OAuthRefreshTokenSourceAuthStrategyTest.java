package co.worklytics.psoxy.gateway.impl.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import co.worklytics.psoxy.utils.RandomNumberGeneratorImpl;
import co.worklytics.test.MockModules;
import dagger.Component;
import lombok.SneakyThrows;

class OAuthRefreshTokenSourceAuthStrategyTest {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RandomNumberGenerator randomNumberGenerator;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        SourceAuthModule.class,
        MockModules.ForConfigService.class,
        MockModules.ForRandomNumberGenerator.class,
    })
    public interface Container {
        void inject(OAuthRefreshTokenSourceAuthStrategyTest test);
    }

    @BeforeEach
    public void setup() {
        OAuthRefreshTokenSourceAuthStrategyTest.Container container =
            DaggerOAuthRefreshTokenSourceAuthStrategyTest_Container.create();
        container.inject(this);

        when(randomNumberGenerator.nextInt(anyInt())).thenReturn(300); //5 minutes
    }

    public static final String EXAMPLE_TOKEN_RESPONSE =
        "{\n" +
        "  \"access_token\" : \"BWjcyMzY3ZDhiNmJkNTY\",\n" +
        "  \"expires_in\" : 3600,\n" +
        "  \"refresh_token\" : \"Srq2NjM5NzA2OWJjuE7c\",\n" +
        "  \"token_type\" : \"bearer\"\n" +
        "}";

    public static final String EXAMPLE_TOKEN_RESPONSE_EXTRA =
        "{\n" +
        "  \"access_token\" : \"BWjcyMzY3ZDhiNmJkNTY\",\n" +
        "  \"expires_in\" : 3600,\n" +
        "  \"refresh_token\" : \"Srq2NjM5NzA2OWJjuE7c\",\n" +
        "  \"token_type\" : \"bearer\",\n" +
        "  \"something_extra\" : \"some-extra-value\",\n" +
        "  \"something_extra_numeric\" : 12,\n" +
        "  \"something_extra_object\" : {\n" +
        "    \"field\" : \"value\"\n" +
        "  }\n" +
        "}";

    @SneakyThrows
    @ValueSource(strings = {EXAMPLE_TOKEN_RESPONSE, EXAMPLE_TOKEN_RESPONSE_EXTRA})
    @ParameterizedTest
    public void tokenResponseJson(String jsonEncoded) {

        CanonicalOAuthAccessTokenResponseDto response =
            objectMapper.readerFor(CanonicalOAuthAccessTokenResponseDto.class).readValue(jsonEncoded);

        assertEquals("bearer", response.getTokenType());
        assertEquals("Srq2NjM5NzA2OWJjuE7c", response.getRefreshToken());
        assertEquals("BWjcyMzY3ZDhiNmJkNTY", response.getAccessToken());
        assertEquals(3600, response.getExpiresIn());

        //reverse
        assertEquals(jsonEncoded,
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
    }

    @Test
    public void testCachedTokenNeedsRefreshWhenNull() {
        OAuthRefreshTokenSourceAuthStrategy strategy = new OAuthRefreshTokenSourceAuthStrategy();
        strategy.randomNumberGenerator = this.randomNumberGenerator;
        assertTrue(strategy.shouldRefresh(null, Instant.now()));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 30_000, 300_000, 3_600_000})
    public void testCachedTokenDoesntNeedRefreshIfNotExpired(int millisBeyondMaxProactiveRefreshUntilTokenExpires) {
        OAuthRefreshTokenSourceAuthStrategy strategy = new OAuthRefreshTokenSourceAuthStrategy();
        Instant fixed = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiration = fixed
            .plus(strategy.MAX_PROACTIVE_TOKEN_REFRESH)
            .plusMillis(millisBeyondMaxProactiveRefreshUntilTokenExpires);

        AccessToken token = new AccessToken("any-token", Date.from(expiration));
        strategy.randomNumberGenerator = this.randomNumberGenerator;
        assertFalse(strategy.shouldRefresh(token, fixed));
    }

    @ParameterizedTest
    @ValueSource(ints = { -3_600_000, -1_000, -1, 0, 1, 1_000, 30_000})
    public void testCachedTokenNeedsRefreshIfExpiredOrCloseTo(int millisAgoThatTokenExpired) {
        OAuthRefreshTokenSourceAuthStrategy strategy = new OAuthRefreshTokenSourceAuthStrategy();
        Instant fixed = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiration = fixed
            .plusMillis(millisAgoThatTokenExpired);

        AccessToken token = new AccessToken("any-token", Date.from(expiration));
        strategy.randomNumberGenerator = this.randomNumberGenerator;
        assertTrue(strategy.shouldRefresh(token, fixed));
    }

    @Test
    public void testProactiveRefreshWithin1MinuteOfExpiration() {
        OAuthRefreshTokenSourceAuthStrategy strategy = new OAuthRefreshTokenSourceAuthStrategy();
        strategy.randomNumberGenerator = this.randomNumberGenerator;
        Instant fixed = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiration = fixed.plusSeconds(58);
        AccessToken token = new AccessToken("any-token", Date.from(expiration));
        assertTrue(strategy.shouldRefresh(token, fixed));
    }



    @SneakyThrows
    @Test
    public void serializesAccessTokenDTO() {
        OAuthRefreshTokenSourceAuthStrategy strategy = new OAuthRefreshTokenSourceAuthStrategy();
        strategy.objectMapper = objectMapper;
        strategy.config = MockModules.provideMock(ConfigService.class);
        strategy.secretStore = MockModules.provideMock(SecretStore.class);
        when(strategy.config.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.USE_SHARED_TOKEN))
            .thenReturn(Optional.of("true"));

        Instant anyTime = Instant.parse("2021-12-15T00:00:00Z");
        strategy.clock = Clock.fixed(anyTime, ZoneOffset.UTC);

        AccessToken token = new AccessToken("my-token", Date.from(anyTime.plus(10_000L, ChronoUnit.MILLIS)));

        // Create a TokenRefreshHandlerImpl to access the storeSharedAccessTokenIfSupported method
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl tokenRefreshHandler = new OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl();
        tokenRefreshHandler.objectMapper = objectMapper;
        tokenRefreshHandler.secretStore = strategy.secretStore;
        tokenRefreshHandler.sourceAuthStrategy = strategy;

        tokenRefreshHandler.storeSharedAccessTokenIfSupported(token);

        verify(strategy.secretStore, times(1)).putConfigProperty(eq(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.ACCESS_TOKEN),
            eq("{\"token\":\"my-token\",\"expirationDate\":1639526410000}"),
            eq(OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl.WRITE_RETRIES));
    }

    @Test
    public void deserializesAccessTokenDTO() {
        OAuthRefreshTokenSourceAuthStrategy strategy = new OAuthRefreshTokenSourceAuthStrategy();
        strategy.objectMapper = objectMapper;
        strategy.config = MockModules.provideMock(ConfigService.class);
        strategy.secretStore = MockModules.provideMock(SecretStore.class);
        when(strategy.secretStore.getAvailableVersions(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.ACCESS_TOKEN, 5))
            .thenReturn(Arrays.asList(
                ConfigService.ConfigValueVersion.builder()
                    .value("{\"token\":\"my-token\",\"expirationDate\":1639526410000}")
                    .version("1")
                    .build()
            ));

        when(strategy.config.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.USE_SHARED_TOKEN))
            .thenReturn(Optional.of("true"));

        Optional<AccessToken> accessToken = strategy.getSharedAccessTokenIfSupported();
        assertTrue(accessToken.isPresent());
        assertEquals("my-token", accessToken.get().getTokenValue());
        assertEquals(1639526410000L, accessToken.get().getExpirationTime().getTime());
    }

    @SneakyThrows
    @Test
    public void choosesTokenByLatestExpiryThenVersion() {
        OAuthRefreshTokenSourceAuthStrategy strategy = new OAuthRefreshTokenSourceAuthStrategy();
        strategy.objectMapper = objectMapper;
        strategy.config = MockModules.provideMock(ConfigService.class);
        strategy.secretStore = MockModules.provideMock(SecretStore.class);

        when(strategy.config.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.USE_SHARED_TOKEN))
            .thenReturn(Optional.of("true"));

        // Build three versions: 1,2,3 with 2 having the furthest expiration
        long now = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli();
        String v1Json = objectMapper.writeValueAsString(new AccessTokenDto("t1", now + 1000));
        String v2Json = objectMapper.writeValueAsString(new AccessTokenDto("t2", now + 10_000)); // largest expiry
        String v3Json = objectMapper.writeValueAsString(new AccessTokenDto("t3", now + 5000));

        List<ConfigService.ConfigValueVersion> versions = Arrays.asList(
            ConfigService.ConfigValueVersion.builder().value(v1Json).version("1").build(),
            ConfigService.ConfigValueVersion.builder().value(v2Json).version("2").build(),
            ConfigService.ConfigValueVersion.builder().value(v3Json).version("3").build()
        );

        when(strategy.secretStore.getAvailableVersions(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.ACCESS_TOKEN, 5))
            .thenReturn(versions);

        Optional<AccessToken> chosen = strategy.getSharedAccessTokenIfSupported();
        assertTrue(chosen.isPresent());
        assertEquals("t2", chosen.get().getTokenValue());
    }

    @SneakyThrows
    @Test
    public void choosesTokenByVersionWhenNoExpiry() {
        OAuthRefreshTokenSourceAuthStrategy strategy = new OAuthRefreshTokenSourceAuthStrategy();
        strategy.objectMapper = objectMapper;
        strategy.config = MockModules.provideMock(ConfigService.class);
        strategy.secretStore = MockModules.provideMock(SecretStore.class);

        when(strategy.config.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.USE_SHARED_TOKEN))
            .thenReturn(Optional.of("true"));

        // Build three versions: 1,2,3 with no expiration dates -> pick highest version (3)
        String v1Json = objectMapper.writeValueAsString(new AccessTokenDto("t1", null));
        String v2Json = objectMapper.writeValueAsString(new AccessTokenDto("t2", null));
        String v3Json = objectMapper.writeValueAsString(new AccessTokenDto("t3", null));

        List<ConfigService.ConfigValueVersion> versions = Arrays.asList(
            ConfigService.ConfigValueVersion.builder().value(v1Json).version("1").build(),
            ConfigService.ConfigValueVersion.builder().value(v2Json).version("2").build(),
            ConfigService.ConfigValueVersion.builder().value(v3Json).version("3").build()
        );

        when(strategy.secretStore.getAvailableVersions(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.ACCESS_TOKEN, 5))
            .thenReturn(versions);

        Optional<AccessToken> chosen = strategy.getSharedAccessTokenIfSupported();
        assertTrue(chosen.isPresent());
        assertEquals("t3", chosen.get().getTokenValue());
    }

    static Stream<Arguments> refreshTokenNotRotated() {
        return Stream.of(
            Arguments.of("old-token", "new-token", true),
            Arguments.of("old-token", "old-token", false),
            Arguments.of("old-token", null, false),
            Arguments.of("old-token", null, false)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource
    public void refreshTokenNotRotated(String originalToken, String newToken, boolean shouldRotate) {
        OAuthRefreshTokenSourceAuthStrategy strategy = new OAuthRefreshTokenSourceAuthStrategy();
        strategy.config = MockModules.provideMock(ConfigService.class);
        strategy.secretStore = MockModules.provideMock(SecretStore.class);
        
        // Create a TokenRefreshHandlerImpl to access the storeRefreshTokenIfRotated method
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl tokenRefreshHandler = new OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl();
        tokenRefreshHandler.config = strategy.config;
        tokenRefreshHandler.secretStore = strategy.secretStore;
        tokenRefreshHandler.sourceAuthStrategy = strategy;
        
        when(strategy.secretStore.getConfigPropertyAsOptional(eq(RefreshTokenTokenRequestBuilder.ConfigProperty.REFRESH_TOKEN)))
            .thenReturn(Optional.of(originalToken));
        when(strategy.secretStore.getConfigPropertyWithMetadata(RefreshTokenTokenRequestBuilder.ConfigProperty.REFRESH_TOKEN))
            .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(originalToken).build()));

        CanonicalOAuthAccessTokenResponseDto exampleResponse = new CanonicalOAuthAccessTokenResponseDto();
        exampleResponse.refreshToken = newToken;
        tokenRefreshHandler.storeRefreshTokenIfRotated(exampleResponse);

        verify(strategy.secretStore, times(shouldRotate ? 1 : 0)).putConfigProperty(eq(RefreshTokenTokenRequestBuilder.ConfigProperty.REFRESH_TOKEN), eq(newToken), eq(OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl.WRITE_RETRIES));
    }

    @Test
    public void refreshProactiveThresholdTimeIsBounded() {
        OAuthRefreshTokenSourceAuthStrategy strategy = new OAuthRefreshTokenSourceAuthStrategy();
        strategy.randomNumberGenerator = new RandomNumberGeneratorImpl();
        IntStream.range(0, 1_000).forEach(i -> {
            int proactiveGracePeriodSeconds = strategy.getProactiveGracePeriodSeconds();
            assertTrue(proactiveGracePeriodSeconds >= strategy.MIN_PROACTIVE_TOKEN_REFRESH.getSeconds());
            assertTrue(proactiveGracePeriodSeconds <= strategy.MAX_PROACTIVE_TOKEN_REFRESH.getSeconds());
        });
    }
}
