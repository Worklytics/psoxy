package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.webtoken.JsonWebSignature;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientCredentialsGrantTokenRequestBuilderTest {

    final String clientId = "60b612e3-a3b0-45d1-a582-4876f286490a";
    final String tokenEndpoint = "https://login.microsoftonline.com/6e4c8e9f-76cf-41d1-806e-61838b880b87/oauth2/v2.0/token";

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


    // example of what github generates, w/o openssl command to reformat
    private final String EXAMPLE_PKCS1 = "-----BEGIN RSA PRIVATE KEY-----\n" +
        "MIIEowIBAAKCAQEAyydvcAkQ4qRWENCouaURHjgKqX2kLCD0MHqjuay9nkDSXTQ/\n" +
        "j5yg/nd/QFNHtVtAG/XdRXPVrpQ11eFWi5/2uEQndUqh+jc52+2dxRllaiDR3D1r\n" +
        "s53SGf8cZ1bdlV+llBLU3e2kTZW/whwQkhT5HXb4yVUPuFEHJ18TVoXeNWjbbLUs\n" +
        "qyjTElB0f3P/W6wBMhk+bb78+Ch1mq4DVrZEV9DlGLCJkBQrtW3EOv0GocScYwRH\n" +
        "XHHK0Dm6Qe6nufRFnmyaHpQElns0RGhkSKgqh/75RbhnW2hrP26I40wD8uDOUEI1\n" +
        "MW8CGOsy47FAy6mhN/0LFNRY45s2Y1D4bWJvCQIDAQABAoIBABTKZWIu21aGgPRZ\n" +
        "llrhZL4V+CleXVXSzYrEkkrnPcSbV6wIM9ULr4I7Un+Pxk/uFcKGx+1arGygaF4K\n" +
        "IKRLa6FyACPFzovf6QDz8WiBb9qLn06Nzp7kMONOM2b0AdtOnZBo2PYZYu45vBUu\n" +
        "cBezI8d7LHzWQrSXPkcuOLlrG9GVSdgBOQW10rTMtFl0mg1HJdhIi7s1nRIbKgO6\n" +
        "zA/DClNzVFo+IT9jEQEj7cRraoJ5Xf+pFMm2pDDPfEfysVi5hNURXGZUGSorwmMa\n" +
        "pggAVIyznfUSrMVcizKVNCcRKlD0pQrbSwkwDTQK8dkiAJFjyBxZxfpw6wbYER2e\n" +
        "DGDTQzECgYEA8OOdWiL5y0Upu9m/vjS1EFOqhMYWzwbZxAvIpHh4QNIdhIoMP6to\n" +
        "LxkHqYtNWR2WxdiVgZUI2FDBEB41ZVn0RJDBVyujQb3cTkyteqXMhyP2hKQgsbPo\n" +
        "GFSdT3auz0tg+zoAjWTJfw1luA7R1ycaRau73bhtrI1PklngfjhSUx0CgYEA1+XX\n" +
        "ZFckcRYJ3Bmp5C1LBc6nXhi9H6PQsV8Tysn4d2KFnrZ4y5w3BZKhTtmghwIG9sEg\n" +
        "Pd3gCdcU7OF2kRBO4Nq+exuIimwIVpJaCMulm6QmlYGygp7we0HT1HTsPTzbPVSc\n" +
        "p5cXi9H+tX6UAIKdRSz9L31/Y47IOeaVsUB9O90CgYAxnvF+682A7dJW+9ffmoPh\n" +
        "xRpPF28DXmnlVHgUSSycTav+7WDwjKJ9cS5+4k8gmFPClYbWlpin1pquc0qUgh8r\n" +
        "MJZjGn4awL1s86aYqSakf+f8EsMZV/HrcSKmh9Aiq2hi1+PdPHG1VlEpxQO8yjVD\n" +
        "PMkKNz+AV+uYPiNcXMW4kQKBgF6HlVqqyRr2slR7rCZrKnkdday+mjg7SsoOviTB\n" +
        "cBgdvDG05YkJGhJHlHdo1F+opJHwF4TfHBRS5yecxIRZpp/PRy2x7YPmL3RwWhmV\n" +
        "ySovonE9u4JzwwnE1dIla7aYacodvQWoIzgmNycSiAz9I41BWI4tndRilQq9Cnf0\n" +
        "q6DFAoGBAJhcljXoVimdRzyU4YjzgXvscvO8Hr8Dm+NkFWDot5DPqBbuW2oula03\n" +
        "3iWLdz+cZPzqVN3wMcCss8bZACYT+ZLVaNlI4fvUYJISCw4NqoWyXEaux4uFsZu8\n" +
        "BGU+vEfLdZg58u5GOZ6gwxEFu0tx8mX3TdbRYhuXtY7P0zgUrzTs\n" +
        "-----END RSA PRIVATE KEY-----";

    @Inject
    ConfigService configService;
    @Inject
    SecretStore secretStore;

    @Inject
    ClientCredentialsGrantTokenRequestBuilder payloadBuilder;

    @Inject
    ObjectMapper objectMapper;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        SourceAuthModule.class,
        TestModules.ForFixedClock.class,
        TestModules.ForFixedUUID.class,
        MockModules.ForConfigService.class,
        MockModules.ForSecretStore.class,
    })
    public interface Container {
        void inject(ClientCredentialsGrantTokenRequestBuilderTest test);
    }

    @BeforeEach
    public void setup() {
        ClientCredentialsGrantTokenRequestBuilderTest.Container container =
            DaggerClientCredentialsGrantTokenRequestBuilderTest_Container.create();
        container.inject(this);

        when(secretStore.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID))
            .thenReturn(clientId);
        when(configService.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT))
            .thenReturn(tokenEndpoint);
        when(configService.getConfigPropertyAsOptional(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.TOKEN_SCOPE))
            .thenReturn(Optional.of("https://graph.microsoft.com/.default"));
    }



    @SneakyThrows
    @Test
    public void tokenRequestPayload_with_jwt() {
        when(secretStore.getConfigPropertyWithMetadata(eq(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY)))
            .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(EXAMPLE_PRIVATE_KEY).build()));
        when(secretStore.getConfigPropertyOrError(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY_ID))
            .thenReturn("F4194D924E8471C804F65E77BCF90418CEEB0DA2");

        final String EXPECTED_ASSERTION = "client_assertion=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsIng1dCI6IjlCbE5razZFY2NnRTlsNTN2UGtFR003ckRhST0ifQ.eyJhdWQiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vNmU0YzhlOWYtNzZjZi00MWQxLTgwNmUtNjE4MzhiODgwYjg3L29hdXRoMi92Mi4wL3Rva2VuIiwiZXhwIjoxNjM5NTI2NzAwLCJpYXQiOjE2Mzk1MjY0MDAsImlzcyI6IjYwYjYxMmUzLWEzYjAtNDVkMS1hNTgyLTQ4NzZmMjg2NDkwYSIsImp0aSI6Ijg4NmNkMmQxLTJhMWQtNDNlOS05MWQ0LTZhMmIxNjZkZmY5ZSIsInN1YiI6IjYwYjYxMmUzLWEzYjAtNDVkMS1hNTgyLTQ4NzZmMjg2NDkwYSJ9.tkGyEKoTPkkn7CXR8w45himxXzlnva0JY9DH_fIfr7uu5zC5BsZmF5HuBdCgU4_rWVPHDUGQmyVyUcRkNZsO9CnHDeHzCoPvWD1FSx8hV3oTwREgjXWQka08PC5ps2wEydSZfPTemP-7AXeIayLl5cWYzS7L_KRylQjNMlrXgMhv5SvUL1lJD76JolX0ksskfBmLldmu99UrMIizREPFkWQUvLE_cX8P9C6mZGl5PB7Ku5kovZAEOrOVQbESUtTUaSdmCEdpGCJz9osvWyksoC1Drp-isKw4FAGwGG6t1BTThL45R2kx-0fQH_jCYiKwLYtedREID9GZourmF8BNdw&client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer&client_id=60b612e3-a3b0-45d1-a582-4876f286490a&grant_type=client_credentials&scope=https%3A%2F%2Fgraph.microsoft.com%2F.default";
        HttpContent payload = payloadBuilder.buildPayload();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        payload.writeTo(out);
        assertEquals(EXPECTED_ASSERTION, out.toString());
    }

    @SneakyThrows
    @Test
    public void tokenRequestPayload_with_client_secret() {
        final String expected_payload = "grant_type=client_credentials&client_secret=fake&client_id=60b612e3-a3b0-45d1-a582-4876f286490a";

        when(configService.getConfigPropertyAsOptional(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.CREDENTIALS_FLOW))
                .thenReturn(Optional.of("client_secret"));
        when(secretStore.getConfigPropertyOrError(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_SECRET))
                .thenReturn("fake");

        HttpContent payload = payloadBuilder.buildPayload();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        payload.writeTo(out);
        assertEquals(expected_payload, out.toString());
    }

    @SneakyThrows
    @Test
    public void keyIdEncoding() {
        //example from MSFT docs (https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-certificate-credentials)
        assertEquals("hOBcHZi846VCHSJbFAs26Go9VTQ=",
            payloadBuilder.encodeKeyId("84E05C1D98BCE3A5421D225B140B36E86A3D5534"));
    }


    @Disabled // around for integration testing; don't want it for usual CI
    @SneakyThrows
    @Test
    public void integration() {
        //thumbprint in console "1F79B46FB49FD94F452F518594EB4D92D3A5A230"
        //example configured in dev "CnG3QJ3kfgjJLjIRAx02maFRekE="
        // "xUCRLwMU99gZwhqvkan4Q7vL4+8zCCoeUIRy6WI0z9Y="

        // x509 thumbprint
        // (can get value directly out of Azure Console, or from AWS SystemParameter after tf apply)
        final String PRIVATE_KEY_ID_FOR_INTEGRATION = "6DDCBAF21BE566331BD6E922A77B62AC0CD62C74";
        // (can get out of AWS SystemParameter after tf apply)
        final String PRIVATE_KEY_FOR_INTEGRATION = "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCoB2fmFON+Ggb2\n" +
            "Jzt+3BoAsZm8LW731Wh1hMtWY2u70ISddJqVtYsBZO/DqQ0Ph4+eKPEAtinYb+5m\n" +
            "3PzPu4jgyrsCCE+iRvM7vBrM1DwRMe8zG8CNGG5ADXeDyrNE2kaYAdM/sP6QWlma\n" +
            "hA2BENYzv+yx/iMEOeAYnDDtGiRqg18FvC9yQQUzZsymqAyMYXkTFBVbjBl3Xs9K\n" +
            "ANKhttw4uIbHCrl9LKsV+R7ZnOX+SsNQBft++tWt8dNj+WoacTz0dMJEQFETD5gV\n" +
            "AtEFIMS3QYuHz6U6/dHGu0EWXH2quYllaJBfdj6tSoq6Uw3gSD2AdquCGtk1YxzD\n" +
            "QGdCnjORAgMBAAECggEBAII22wmu/m1m9iYkWTMClxQaji0KeIiPVZhdBMc53O97\n" +
            "tInhJzsFqWe3NSfIBlsWjvHegIYwpVUZyQLmFvVVO8oY0bvNfQkhOrX8HDjH8JTS\n" +
            "wbA1vY6adDYnOYtktnCRR0vdfjxJib2Mhwv7cgunZJhOD7wQWkqYH1ZzFGdqbvYq\n" +
            "/vuIZXG/2IoDY6/yth2P4TWdsqpC5rm9+CrCXP/f7bQtXXs1E14Jek0WZobazwah\n" +
            "4Nk4AlUEzZihCHjvXRSHM5T+Uk4p/U6ISU9dv02buNAbHUQzv06Wu0pgW69E7xnZ\n" +
            "brW7VBSV2EnmfXqkbmgpYZzVX7QYmOJOsO6piiMf68ECgYEA1ueyIQIPKl9GU92+\n" +
            "CA/tdI4qOjHJi4BTIciOhWPTOF0N7cuFMIfVWuqTSBcMP1F+S3sTLcaRNjomYZz8\n" +
            "6KMW9nIRD7+uqRWQl1VJXfATnXqmFrzA/EzIFAaXLtGaBhgn1ul5cV8Kd0eEhTt6\n" +
            "JA+UK79zIZJFR0BBhWxcKf2VjJ0CgYEAyCj4aNW+1nv/f3tl/Bjm8tl6vZXXhK/o\n" +
            "42VJiO5N3fHo+KEZ6ocmcCyaoCeCusomfsCFEBGyAyW7wK6xEZD6ONF0xz3/5/E1\n" +
            "kqUIy2+51Kcva4WiMIKDWzt0d866DCd8GcJkDY/OIDvScc0l2OA/4rRL6ybbAk6d\n" +
            "Bzuo+n0s3oUCgYBTJjCAnvhZL6XZWylkmy0H9N2XyJ2vkQYZQy0JpVcbLr3t7Nnq\n" +
            "rhO56przwJ8nfJN+Bu+jvXl/3r3s9L3SERAYaIf7bPHaUBKyyvfpFbOxMbxDfeK5\n" +
            "e8fKH8atAcIza3M2rv0jBV/aSNyYZCvc+f4dcyTLr3mImO8A/a0nPgt37QKBgQCa\n" +
            "gz4Xt4DlE72NDJYSwKpvp8DfXy+KxzyxZXwZj1Re06KzY7Gc4Q2kJFqM7VM2nFyR\n" +
            "Fk7hs7dGRLemK3SXCeKPP+m08MB+rS5c8LdUTAAZD6JEj1k/t1Btef0Ti2sFfOmI\n" +
            "/Q29hlhpe6Sdou7nd1z5xZKhiVIheswvTDfKfhzH/QKBgDfSN5Bg+XGv7RWxGpPs\n" +
            "VRjx2iq/SNuRFLt6ezirwoDqKly9b2zrDJM5d8qzABsmMtTGvzbxxFcOs/UsJeJa\n" +
            "G5V7N45KStzMeTzuT4DFTtGGJTrzrms+371Vl51Fg1xuewNcbuMjU8oaXAxlWalL\n" +
            "m8lD1czHbMIsv1EHZj/GcCIa\n" +
            "-----END PRIVATE KEY-----";

        when(secretStore.getConfigPropertyOrError(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(PRIVATE_KEY_FOR_INTEGRATION);
        when(secretStore.getConfigPropertyOrError(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY_ID))
            .thenReturn(PRIVATE_KEY_ID_FOR_INTEGRATION);

        HttpRequestFactory requestFactory = (new NetHttpTransport()).createRequestFactory();

        payloadBuilder.clock = Clock.systemUTC();
        HttpRequest request = requestFactory.buildPostRequest(new GenericUrl(tokenEndpoint), payloadBuilder.buildPayload());

        HttpResponse response = request.execute();
        assertEquals(200, response.getStatusCode());

        String content = new String(response.getContent().readAllBytes());
        CanonicalOAuthAccessTokenResponseDto tokenResponse =
            objectMapper.readerFor(CanonicalOAuthAccessTokenResponseDto.class)
                .readValue(content);

        assertEquals("Bearer", tokenResponse.getTokenType());
        assertNotNull(tokenResponse.getAccessToken());
        assertNotNull(tokenResponse.getExpiresIn());
    }

    @ValueSource(strings = {
        "6FCC8E28F6A63B4E994ED62F52BDF3C3B0B7E88B",
        "  6FCC8E28F6A63B4E994ED62F52BDF3C3B0B7E88B  ",
        "sha1 Fingerprint=6FCC8E28F6A63B4E994ED62F52BDF3C3B0B7E88B",
        "  sha1 Fingerprint=6FCC8E28F6A63B4E994ED62F52BDF3C3B0B7E88B  ",
        "6F:CC:8E:28:F6:A6:3B:4E:99:4E:D6:2F:52:BD:F3:C3:B0:B7:E8:8B",
        "6fcc8e28f6a63b4e994ed62f52bdf3c3b0b7e88b",
    })
    @ParameterizedTest
    public void setJWTCustomHeaders(String configuredPrivateKeyId) {
        JsonWebSignature.Header header = mock(JsonWebSignature.Header.class);

        when(secretStore.getConfigPropertyOrError(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY_ID))
            .thenReturn(configuredPrivateKeyId);
        payloadBuilder.setJWTCustomHeaders(header);
        verify(header, times(1))
            .setX509Thumbprint(eq(payloadBuilder.encodeKeyId("6FCC8E28F6A63B4E994ED62F52BDF3C3B0B7E88B")));
    }

    @SneakyThrows
    @Test
    public void getPrivateKey_variousFormats() {

        // 0. Plain private key
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(Optional.empty());
        String plain = EXAMPLE_PRIVATE_KEY;
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(plain).build()));
        assertNotNull(payloadBuilder.getPrivateKey(), "Should parse base64-encoded private key");

        // 1. Base64-encoded private key
        String base64Encoded = java.util.Base64.getEncoder().encodeToString(EXAMPLE_PRIVATE_KEY.getBytes());
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(base64Encoded).build()));
        assertNotNull(payloadBuilder.getPrivateKey(), "Should parse base64-encoded private key");

        // 2. Private key with extra whitespace
        String withWhitespace = "   " + EXAMPLE_PRIVATE_KEY + "   ";
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(withWhitespace).build()));
        assertNotNull(payloadBuilder.getPrivateKey(), "Should parse private key with extra whitespace");

        // 3. Private key with extra new lines
        String withNewlines = "\r\n  \r\n " + EXAMPLE_PRIVATE_KEY + "  \n\r\n";
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(withNewlines).build()));
        assertNotNull(payloadBuilder.getPrivateKey(), "Should parse private key with extra new lines");
    }


    @SneakyThrows
    @Test
    public void getPkcs1Key_variousFormats() {

        // 0. Plain private key
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(Optional.empty());
        String plain = EXAMPLE_PKCS1;
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(plain).build()));
        assertNotNull(payloadBuilder.getPrivateKey(), "Should parse base64-encoded private key");

        // 1. Base64-encoded private key
        String base64Encoded = java.util.Base64.getEncoder().encodeToString(EXAMPLE_PKCS1.getBytes());
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(base64Encoded).build()));
        assertNotNull(payloadBuilder.getPrivateKey(), "Should parse base64-encoded private key");

        // 2. Private key with extra whitespace
        String withWhitespace = "   " + EXAMPLE_PKCS1 + "   ";
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(withWhitespace).build()));
        assertNotNull(payloadBuilder.getPrivateKey(), "Should parse private key with extra whitespace");

        // 3. Private key with extra new lines
        String withNewlines = "\r\n  \r\n " + EXAMPLE_PKCS1 + "  \n\r\n";
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
            .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(withNewlines).build()));
        assertNotNull(payloadBuilder.getPrivateKey(), "Should parse private key with extra new lines");
    }

    @SneakyThrows
    @Test
    public void parsePKCS1() {
        assertNotNull(payloadBuilder.parsePrivateKey(EXAMPLE_PKCS1));
    }

    @SneakyThrows
    @Test
    public void parsePKCS8() {
        assertNotNull(payloadBuilder.parsePrivateKey(EXAMPLE_PRIVATE_KEY));
    }

}
