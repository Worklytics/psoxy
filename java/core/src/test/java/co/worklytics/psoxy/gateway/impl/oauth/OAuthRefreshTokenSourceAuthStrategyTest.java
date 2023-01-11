package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.test.MockModules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthRefreshTokenSourceAuthStrategyTest {

    @Inject
    ObjectMapper objectMapper;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        SourceAuthModule.class,
        MockModules.ForConfigService.class
    })
    public interface Container {
        void inject(OAuthRefreshTokenSourceAuthStrategyTest test);

    }

    @BeforeEach
    public void setup() {
        OAuthRefreshTokenSourceAuthStrategyTest.Container container =
            DaggerOAuthRefreshTokenSourceAuthStrategyTest_Container.create();
        container.inject(this);
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
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl tokenRefreshHandler = new OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl();
        assertFalse(tokenRefreshHandler.isCurrentTokenValid(null, Instant.now()));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3_600_000})
    public void testCachedTokenDoesntNeedRefreshIfNotExpired(int millis) {
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl tokenRefreshHandler = new OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl();
        Instant fixed = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiration = fixed.plus(tokenRefreshHandler.TOKEN_REFRESH_THRESHOLD).plusMillis(millis);

        AccessToken token = new AccessToken("any-token", Date.from(expiration));
        assertTrue(tokenRefreshHandler.isCurrentTokenValid(token, fixed));
    }

    @ParameterizedTest
    @ValueSource(ints = { -3_600_000, -1, 0})
    public void testCachedTokenNeedsRefreshIfExpiredOrCloseTo(int millis) {
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl tokenRefreshHandler = new OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl();
        Instant fixed = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiration = fixed.plus(tokenRefreshHandler.TOKEN_REFRESH_THRESHOLD).plusMillis(millis);

        AccessToken token = new AccessToken("any-token", Date.from(expiration));
        assertFalse(tokenRefreshHandler.isCurrentTokenValid(token, fixed));
    }

    @Test
    public void serializesAccessTokenDTO() {
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl tokenRefreshHandler = new OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl();
        tokenRefreshHandler.objectMapper = objectMapper;
        tokenRefreshHandler.config = mock(ConfigService.class);
        tokenRefreshHandler.payloadBuilder = mock(OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder.class);
        when(tokenRefreshHandler.payloadBuilder.useSharedToken()).thenReturn(true);
        Instant anyTime = Instant.parse("2021-12-15T00:00:00Z");
        tokenRefreshHandler.clock = Clock.fixed(anyTime, ZoneOffset.UTC);


        AccessToken token = new AccessToken("my-token", Date.from(anyTime.plus(10_000L, ChronoUnit.MILLIS)));

        tokenRefreshHandler.storeSharedAccessTokenIfSupported(token);

        verify(tokenRefreshHandler.config, times(1)).putConfigProperty(eq(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.ACCESS_TOKEN),
            eq("{\"token\":\"my-token\",\"expirationDate\":1639526410000}"));
    }

    @Test
    public void deserializesAccessTokenDTO() {
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl tokenRefreshHandler = new OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl();
        tokenRefreshHandler.objectMapper = objectMapper;
        tokenRefreshHandler.config = mock(ConfigService.class);
        when(tokenRefreshHandler.config.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.ACCESS_TOKEN)).thenReturn(Optional.of("{\"token\":\"my-token\",\"expirationDate\":1639526410000}"));

        tokenRefreshHandler.payloadBuilder = mock(OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder.class);
        when(tokenRefreshHandler.payloadBuilder.useSharedToken()).thenReturn(true);

        Optional<AccessToken> accessToken = tokenRefreshHandler.getSharedAccessTokenIfSupported();
        assertTrue(accessToken.isPresent());
        assertEquals("my-token", accessToken.get().getTokenValue());
        assertEquals(1639526410000L, accessToken.get().getExpirationTime().getTime());
    }

    static Stream<Arguments> refreshTokenNotRotated() {
        return Stream.of(
            Arguments.of("old-token", "new-token", true),
            Arguments.of("old-token", "old-token", false),
            Arguments.of("old-token", null, false),
            Arguments.of("old-token", null, false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void refreshTokenNotRotated(String originalToken, String newToken, boolean shouldRotate) {
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl tokenRefreshHandler = new OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl();
        tokenRefreshHandler.config = spy(ConfigService.class);
        when(tokenRefreshHandler.config.supportsWriting()).thenReturn(true);
        when(tokenRefreshHandler.config.getConfigPropertyAsOptional(eq(RefreshTokenBuilder.ConfigProperty.REFRESH_TOKEN)))
            .thenReturn(Optional.of(originalToken));

        CanonicalOAuthAccessTokenResponseDto exampleResponse = new CanonicalOAuthAccessTokenResponseDto();
        exampleResponse.refreshToken = newToken;
        tokenRefreshHandler.storeRefreshTokenIfRotated(exampleResponse);

        verify(tokenRefreshHandler.config, times(shouldRotate ? 1 : 0)).putConfigProperty(eq(RefreshTokenBuilder.ConfigProperty.REFRESH_TOKEN), eq(newToken));
    }
}
