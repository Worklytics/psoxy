package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.test.MockModules;
import com.google.api.client.http.HttpContent;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RefreshTokenRequestBuilderTest {

    @Inject
    SecretStore secretStore;

    @Inject
    RefreshTokenTokenRequestBuilder refreshTokenPayloadBuilder;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        SourceAuthModule.class,
        MockModules.ForConfigService.class,
        MockModules.ForSecretStore.class
    })
    public interface Container {
        void inject(RefreshTokenRequestBuilderTest test);
    }

    @BeforeEach
    public void setup() {
        RefreshTokenRequestBuilderTest.Container container =
            DaggerRefreshTokenRequestBuilderTest_Container.create();
        container.inject(this);
    }


    @SneakyThrows
    @Test
    public void tokenRequestPayload() {

        when(secretStore.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID))
            .thenReturn("1");
        when(secretStore.getConfigPropertyOrError(RefreshTokenTokenRequestBuilder.ConfigProperty.REFRESH_TOKEN))
            .thenReturn("tokenValue");
        when(secretStore.getConfigPropertyOrError(RefreshTokenTokenRequestBuilder.ConfigProperty.CLIENT_SECRET))
            .thenReturn("secretValue");

        HttpContent payload = refreshTokenPayloadBuilder.buildPayload();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        payload.writeTo(out);
        assertEquals("refresh_token=tokenValue&grant_type=refresh_token&client_secret=secretValue&client_id=1",
            out.toString());
    }

    @Test
    public void getEndpoint() {
        String baseUrl = "https://some-auth-endpoint.com/token";

        URI result = refreshTokenPayloadBuilder.getEndpoint(baseUrl);

        URI expectedUrl = URI.create(baseUrl);
        assertEquals(expectedUrl, result);
    }
}
