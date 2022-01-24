package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
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

class ClientCredentialsGrantTokenRequestPayloadBuilderTest {

    private final String EXAMPLE_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n" +
        "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDLGozRKkmG1fDg\n" +
        "AkfF3HL+AxYwWHLmVNT627nXTKXyAeYU1XKHhjBooltJlDzJt4h2BbQXQcVy/neD\n" +
        "NKHrSnjHaoc9ykcJuJ7N67nxNKsMIPeLIMVlTdIRs65i+QgysqRALafzpANbRcyG\n" +
        "xeaXDOwfbxMe8WdtcHado/8ZMo2ULKSrZBwiprOnpeeaj57YQCBJqH/4FnM7O/xc\n" +
        "CfMmKX0Awy+mmVHOHJ5PuWwkticHakiRJJI7A/74uEBToTeRREA6ODqSXe7hafXG\n" +
        "DGxLcyVN7H8OKQS6aZYxVMbc4pBVd3Bc4MVWPrI0Fh+biD317f53g9uVLJOlcwfI\n" +
        "kjBnsA15AgMBAAECggEAV9yZdFbFzgW5GT8DS44NVOOC8tEgi5HBPPBW2dO3qhS2\n" +
        "MucPj582S92IfjEZhu3Vo7Dd8n2qbA+3JdRcA4yI6UOlU86wonzyfgRuwPDW2f1c\n" +
        "+RvpKOTcbnn7g6dVq5DqSzU1Bco7BX4Rd3AfS9L0RrjnXahy5L4VIiby0TS0WJed\n" +
        "ZmrmWM4lrm8mMYggJ7uRMcl6SyrrdvlVs04QUrTt78Cc9YHkyagnmqdqHZLyNvy+\n" +
        "bq5rqw9LSVHZUDBbBaCS/PYx23ESvGIvaj8DCIgKss6BrJS4Gm8LA0n9LyRMTxSQ\n" +
        "m9Q/ak3ErGOlMsXICYldusCXNG3XrnjTNXl3Yrr8sQKBgQD5TSLfmTaMLtQx660g\n" +
        "BiAEjeC33Mzr/USTBgo40Jjf0SvLzQaLU/4xN2fdWP56lZUyohL7TUhiPUtkMTEj\n" +
        "FnWme+/xbMyOTdV94ar7eATgdLfEDorhSAlqoEHWaHuBLFkfIsPWLMj2AJQYy3zv\n" +
        "vF9Pg8FwRVANh5/HUQWwo7K5rQKBgQDQj6KmjotfzYKKfPT0cUaX6lAu27wJaiaB\n" +
        "DRPXHSUw+i9aVN/uXXffJDFCwc+2ZVzetrBWQPyYeT7gCPWHCgoJizMtDwocfRsX\n" +
        "6XOR3EwUn1VAP5t+4Tj1l+jPxnEWTLbpkmpX5Fz+fI4VvnReD1usJJR3taJj0aoY\n" +
        "9aIJPY10fQKBgQDkgUVjuaV73Dh3AVQQNE0In5rILERQUjaWpESeSuS00Z7ZELXc\n" +
        "40HsjqJPCpFGfvDgFhpb6Txdf10pGsW1KZLw5EzL9zOPg9wZo8z67claiuEdfU7i\n" +
        "qwqmhvmRsvbxMVsG5PCn0SjqAyG7kFiVzQ13bobRh1aW8CcNpEAY5hKdiQKBgQCr\n" +
        "zUZs8Ys6Fe5s6lZWbmF6jtgSYYvaLYkeUYmAcE/MsqsDPFti3bf7JrM1jLXwSDti\n" +
        "cxd7Vfk+GNKEdpza6pguGG7FtVfc8+m3nuVGyDQb4My3Ki6LLDhhhwuO7KcHeZZL\n" +
        "fgL/9+vq7uuUWdk+CmS3v0JWAleM5o+6E82w0vWNeQKBgQCOUZjANa8pAJdKo3BD\n" +
        "jiTRTRChfn5FLO6PLjiPSFK7m++uUnCJUDLMpcpVV7QpOsgulvlCnvJskjFXvoQ3\n" +
        "cAp3OU8opyEHtqaUBhFtzmfJZrNdsHonVOFWR9gcVEX7caaJYBV4SB04eqFSXnYj\n" +
        "VYvh13TyCO+EUuJ2js2zcn30Ug==\n" +
        "-----END PRIVATE KEY-----";

    @Inject
    ConfigService configService;

    @Inject
    ClientCredentialsGrantTokenRequestPayloadBuilder payloadBuilder;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        SourceAuthModule.class,
        TestModules.ForFixedClock.class,
        TestModules.ForFixedUUID.class,
        MockModules.ForConfigService.class,
    })
    public interface Container {
        void inject(ClientCredentialsGrantTokenRequestPayloadBuilderTest test);
    }

    @BeforeEach
    public void setup() {
        ClientCredentialsGrantTokenRequestPayloadBuilderTest.Container container =
            DaggerClientCredentialsGrantTokenRequestPayloadBuilderTest_Container.create();
        container.inject(this);
    }



    @SneakyThrows
    @Test
    public void tokenRequestPayload() {

        when(configService.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID))
            .thenReturn("1");
        when(configService.getConfigPropertyOrError(ClientCredentialsGrantTokenRequestPayloadBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(EXAMPLE_PRIVATE_KEY);
        when(configService.getConfigPropertyOrError(ClientCredentialsGrantTokenRequestPayloadBuilder.ConfigProperty.PRIVATE_KEY_ID))
            .thenReturn("asdfasdfa");

        final String EXPECTED_ASSERTION = "client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer&grant_type=client_credentials&client_assertion=eyJhbGciOiJSUzI1NiIsIng1dCI6ImFzZGZhc2RmYSJ9.eyJleHAiOjE2Mzk1MjY3MDAsImlhdCI6MTYzOTUyNjQwMCwiaXNzIjoiMSIsImp0aSI6Ijg4NmNkMmQxLTJhMWQtNDNlOS05MWQ0LTZhMmIxNjZkZmY5ZSIsInN1YiI6IjEifQ.AMzvW80R9x8oiDlNAFoY-xNGTJKIvcJgHnS--YltbL7X83AS_m8piicKMtELcZtO6pTNdqwzxvyG1Z9wFWeWnU3SsnZgr9XNNDqdHVaSk6R46RA8SiHNxsFXfCZUHOkCXuGcSzSSI6O4_huS5WaDVBGjBLUr8JAtZj9sNnu7vaBvUb8aho5SJmWQT9Maf41jr7wden1Auea7bApavudLpMJwgYpLMz0xlR2VKYbF7tmw6cPT4lKZyuHCz6por8vyo3B7OCT6tyKYV401sbxvy7sZTAJROHSEVPkv7ShSVK0QNBo2u3d-fNA60SHMIzzTBJ4y7NbuXAsxCkVNd-i78A&client_id=1";
        HttpContent payload = payloadBuilder.buildPayload();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        payload.writeTo(out);
        assertEquals(EXPECTED_ASSERTION, out.toString());
    }
}
