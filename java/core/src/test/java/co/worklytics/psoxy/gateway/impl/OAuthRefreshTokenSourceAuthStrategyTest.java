package co.worklytics.psoxy.gateway.impl;

import com.google.api.client.http.HttpContent;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OAuthRefreshTokenSourceAuthStrategyTest {




    @SneakyThrows
    @Test
    public void tokenResponseJson() {
        OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl refreshHandler =
            new OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl();

        OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl.CanonicalOAuthAccessTokenResponseDto response = refreshHandler.objectMapper.readerFor(OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl.CanonicalOAuthAccessTokenResponseDto.class)
            .readValue(EXAMPLE_TOKEN_RESPONSE);

        assertEquals("bearer", response.getTokenType());
        assertEquals("Srq2NjM5NzA2OWJjuE7c", response.getRefreshToken());
        assertEquals("BWjcyMzY3ZDhiNmJkNTY", response.getAccessToken());
        assertEquals(3600, response.getExpires());
    }

    public static final String EXAMPLE_TOKEN_RESPONSE = "{\n" +
        "         \"access_token\": \"BWjcyMzY3ZDhiNmJkNTY\",\n" +
        "         \"refresh_token\": \"Srq2NjM5NzA2OWJjuE7c\",\n" +
        "         \"token_type\": \"bearer\",\n" +
        "         \"expires\": 3600\n" +
        "         }";


    @SneakyThrows
    @Test
    public void tokenRequestPayload() {
        OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl refreshHandler =
            new OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl();

        Map<String, String> configValues = new HashMap<>();
        configValues.put(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID.name(), "1");
        configValues.put(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_TOKEN.name(), "tokenValue");
        configValues.put(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_SECRET.name(), "secretValue");
        refreshHandler.config = new MemoryConfigService(configValues);

        HttpContent payload = refreshHandler.tokenRequestPayload();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        payload.writeTo(out);
        assertEquals("refresh_token=tokenValue&grant_type=refresh_token&client_secret=secretValue&client_id=1",
            out.toString());
    }
}
