package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.test.MockModules;
import com.google.api.client.http.HttpContent;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RefreshTokenPayloadBuilderTest {

    @Inject
    ConfigService configService;

    @Inject
    RefreshTokenPayloadBuilder refreshTokenPayloadBuilder;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        SourceAuthModule.class,
        MockModules.ForConfigService.class,
    })
    public interface Container {
        void inject(RefreshTokenPayloadBuilderTest test);
    }

    @BeforeEach
    public void setup() {
        RefreshTokenPayloadBuilderTest.Container container =
            DaggerRefreshTokenPayloadBuilderTest_Container.create();
        container.inject(this);
    }


    @SneakyThrows
    @Test
    public void tokenRequestPayload() {

        when(configService.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID))
            .thenReturn("1");
        when(configService.getConfigPropertyOrError(RefreshTokenPayloadBuilder.ConfigProperty.REFRESH_TOKEN))
            .thenReturn("tokenValue");
        when(configService.getConfigPropertyOrError(RefreshTokenPayloadBuilder.ConfigProperty.CLIENT_SECRET))
            .thenReturn("secretValue");

        HttpContent payload = refreshTokenPayloadBuilder.buildPayload();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        payload.writeTo(out);
        assertEquals("refresh_token=tokenValue&grant_type=refresh_token&client_secret=secretValue&client_id=1",
            out.toString());
    }
}
