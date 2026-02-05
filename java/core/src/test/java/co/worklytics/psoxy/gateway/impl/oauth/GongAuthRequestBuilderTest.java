package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.test.MockModules;
import com.google.api.client.http.HttpHeaders;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class GongAuthRequestBuilderTest {

    @Inject
    SecretStore secretStore;

    @Inject
    GongAuthRequestBuilder requestBuilder;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        SourceAuthModule.class,
        MockModules.ForConfigService.class,
        MockModules.ForSecretStore.class
    })
    public interface Container {
        void inject(GongAuthRequestBuilderTest test);
    }

    @BeforeEach
    public void setup() {
        GongAuthRequestBuilderTest.Container container =
            DaggerGongAuthRequestBuilderTest_Container.create();
        container.inject(this);
    }

    @SneakyThrows
    @Test
    public void addHeaders_shouldSetBasicAuthHeader() {
        String clientId = "test-client-id";
        String clientSecret = "test-client-secret";

        when(secretStore.getConfigPropertyOrError(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID))
            .thenReturn(clientId);
        when(secretStore.getConfigPropertyOrError(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_SECRET))
            .thenReturn(clientSecret);

        HttpHeaders headers = new HttpHeaders();
        requestBuilder.addHeaders(headers);

        // Verify Basic Auth header is correctly set
        String expectedToken = Base64.getEncoder()
            .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String expectedAuth = "Basic " + expectedToken;

        assertEquals(expectedAuth, headers.getAuthorization());
    }

    @Test
    public void addHeaders_shouldTrimClientCredentials() {
        String clientId = "  test-client-id  ";
        String clientSecret = "  test-client-secret  ";

        when(secretStore.getConfigPropertyOrError(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID))
            .thenReturn(clientId);
        when(secretStore.getConfigPropertyOrError(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_SECRET))
            .thenReturn(clientSecret);

        HttpHeaders headers = new HttpHeaders();
        requestBuilder.addHeaders(headers);

        // Verify credentials are trimmed before encoding
        String expectedToken = Base64.getEncoder()
            .encodeToString(("test-client-id:test-client-secret").getBytes(StandardCharsets.UTF_8));
        String expectedAuth = "Basic " + expectedToken;

        assertEquals(expectedAuth, headers.getAuthorization());
    }

    @Test
    public void addQueryParameters_shouldAppendGrantTypeAndRefreshToken() {
        String refreshToken = "test-refresh-token";
        String baseUrl = "https://app.gong.io/oauth2/generate-customer-token";

        when(secretStore.getConfigPropertyOrError(RefreshTokenTokenRequestBuilder.ConfigProperty.REFRESH_TOKEN))
            .thenReturn(refreshToken);

        String result = requestBuilder.addQueryParameters(baseUrl);

        String expectedUrl = baseUrl + "?grant_type=refresh_token&refresh_token=" + refreshToken;
        assertEquals(expectedUrl, result);
    }

    @Test
    public void buildPayload_shouldReturnEmptyContent() {
        // Since Gong uses query parameters instead of POST body
        assertNotNull(requestBuilder.buildPayload());
    }

    @Test
    public void getGrantType_shouldReturnGongRefreshToken() {
        assertEquals("gong_refresh_token", requestBuilder.getGrantType());
    }

    @Test
    public void getRequiredConfigProperties_shouldIncludeAllNecessaryProperties() {
        var requiredProps = requestBuilder.getRequiredConfigProperties();

        assertEquals(3, requiredProps.size());
        assertTrue(requiredProps.contains(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID));
        assertTrue(requiredProps.contains(GongAuthRequestBuilder.ConfigProperty.CLIENT_SECRET));
        assertTrue(requiredProps.contains(GongAuthRequestBuilder.ConfigProperty.REFRESH_TOKEN));
    }
}

