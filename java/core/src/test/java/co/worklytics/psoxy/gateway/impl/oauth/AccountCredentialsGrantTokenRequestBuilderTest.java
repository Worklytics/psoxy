package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.test.MockModules;
import com.google.api.client.http.HttpHeaders;
import dagger.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class AccountCredentialsGrantTokenRequestBuilderTest {

    @Inject
    AccountCredentialsGrantTokenRequestBuilder payloadBuilder;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        SourceAuthModule.class,
        MockModules.ForConfigService.class,
        MockModules.ForSecretStore.class,
    })
    public interface Container {
        void inject(AccountCredentialsGrantTokenRequestBuilderTest test);
    }

    @BeforeEach
    public void setup() {
        AccountCredentialsGrantTokenRequestBuilderTest.Container container =
            DaggerAccountCredentialsGrantTokenRequestBuilderTest_Container.create();
        container.inject(this);
    }

    @Test
    void addHeaders() {
        when(payloadBuilder.secretStore
            .getConfigPropertyOrError(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID))
            .thenReturn("client");
        when(payloadBuilder.secretStore
            .getConfigPropertyOrError(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_SECRET))
            .thenReturn("secret");

        HttpHeaders headers = new HttpHeaders();
        payloadBuilder.addHeaders(headers);

        assertEquals("Basic Y2xpZW50OnNlY3JldA==", headers.getAuthorization());
    }
}
