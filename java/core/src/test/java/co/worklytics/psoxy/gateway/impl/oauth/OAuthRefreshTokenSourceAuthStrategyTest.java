package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.test.MockModules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class OAuthRefreshTokenSourceAuthStrategyTest {

    @Inject
    ObjectMapper objectMapper;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        SourceAuthModule.class,
        MockModules.ForConfigService.class,
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
        assertFalse(tokenRefreshHandler.isPreviousTokenValid(null, Instant.now()));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3_600_000})
    public void testCachedTokenDoesntNeedRefreshIfNotExpired(int millis) {
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl tokenRefreshHandler = new OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl();
        Instant fixed = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiration = fixed.plus(tokenRefreshHandler.TOKEN_REFRESH_THRESHOLD).plusMillis(millis);

        AccessToken token = new AccessToken("any-token", Date.from(expiration));
        assertTrue(tokenRefreshHandler.isPreviousTokenValid(token, fixed));
    }

    @ParameterizedTest
    @ValueSource(ints = { -3_600_000, -1, 0})
    public void testCachedTokenNeedsRefreshIfExpiredOrCloseTo(int millis) {
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl tokenRefreshHandler = new OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl();
        Instant fixed = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant expiration = fixed.plus(tokenRefreshHandler.TOKEN_REFRESH_THRESHOLD).plusMillis(millis);

        AccessToken token = new AccessToken("any-token", Date.from(expiration));
        assertFalse(tokenRefreshHandler.isPreviousTokenValid(token, fixed));
    }

}
