package co.worklytics.psoxy.gateway.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpContent;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OAuthRefreshTokenSourceAuthStrategyTest {

    public static final String EXAMPLE_TOKEN_RESPONSE =
        "{\n" +
        "  \"access_token\" : \"BWjcyMzY3ZDhiNmJkNTY\",\n" +
        "  \"expires\" : 3600,\n" +
        "  \"refresh_token\" : \"Srq2NjM5NzA2OWJjuE7c\",\n" +
        "  \"token_type\" : \"bearer\"\n" +
        "}";

    public static final String EXAMPLE_TOKEN_RESPONSE_EXTRA =
        "{\n" +
        "  \"access_token\" : \"BWjcyMzY3ZDhiNmJkNTY\",\n" +
        "  \"expires\" : 3600,\n" +
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
        OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl refreshHandler =
            new OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl();

        OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl.CanonicalOAuthAccessTokenResponseDto response = refreshHandler.objectMapper.readerFor(OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl.CanonicalOAuthAccessTokenResponseDto.class)
            .readValue(jsonEncoded);

        assertEquals("bearer", response.getTokenType());
        assertEquals("Srq2NjM5NzA2OWJjuE7c", response.getRefreshToken());
        assertEquals("BWjcyMzY3ZDhiNmJkNTY", response.getAccessToken());
        assertEquals(3600, response.getExpires());

        //reverse
        ObjectMapper objectMapper = new ObjectMapper();
        assertEquals(jsonEncoded,
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
    }

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
