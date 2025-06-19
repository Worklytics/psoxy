package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.WebhookCollectorModeConfigProperty;
import co.worklytics.psoxy.gateway.auth.Base64KeyClient;
import co.worklytics.psoxy.gateway.auth.JwtAuthorizedResource;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import co.worklytics.psoxy.gateway.impl.output.NoOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JwksDecoratorTest {

    JwksDecorator handler;
    ConfigService configService;
    Set<PublicKeyStoreClient> keyClients;

    static final String EXPECTED_JSON = """
    {
      "keys": [
        {
          "kty": "RSA",
          "alg": "RS256",
          "use": "sig",
          "kid": "%s",
          "n": "%s",
          "e": "%s"
        }
      ]
    }
    """;

    String generatedBase64Key;

    @BeforeEach
    void setUp() throws Exception {
        // Generate a valid RSA key pair and encode the public key in X.509/SPKI format
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        generatedBase64Key = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        configService = mock(ConfigService.class);
        when(configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.ACCEPTED_AUTH_KEYS))
            .thenReturn(java.util.Optional.of("base64:" + generatedBase64Key));
        keyClients = Collections.singleton(new Base64KeyClient());
        handler = new JwksDecorator(new InboundWebhookHandler(
            () -> mock(WebhookSanitizer.class), // Mocked WebhookSanitizer
            new NoOutput(),
            configService,
            keyClients
        ));
    }

    @Test
    void testJwksResponse() {
        HttpEventRequest request = mock(HttpEventRequest.class);
        when(request.getPath()).thenReturn("/.well-known/jwks.json");

        HttpEventResponse response = handler.handle(request);
        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        // Dynamically fill in kid, n, e for the expected JSON
        java.util.Iterator<java.security.interfaces.RSAPublicKey> it = handler.jwtAuthorizedResource.acceptableAuthKeys().iterator();
        java.security.interfaces.RSAPublicKey key = it.next();
        String kid = Integer.toHexString(key.hashCode());
        String n = JwksDecorator.JWK.fromRSAPublicKey(key).n;
        String e = JwksDecorator.JWK.fromRSAPublicKey(key).e;
        String expectedJson = String.format(EXPECTED_JSON, kid, n, e).replaceAll("\\s+", "");
        String actualJson = response.getBody().replaceAll("\\s+", "");
        assertEquals(expectedJson, actualJson);
    }

    @Test
    void testInvalidPath() {
        HttpEventRequest request = mock(HttpEventRequest.class);
        when(request.getPath()).thenReturn("/not-jwks");
        assertThrows(IllegalArgumentException.class, () -> handler.handle(request));
    }

    @Test
    void testOpenIdConfigResponse() {
        // Arrange
        HttpEventRequest request = mock(HttpEventRequest.class);
        when(request.getPath()).thenReturn("/.well-known/openid-configuration");

        // Mock JwtAuthorizedResource
        JwtAuthorizedResource jwtAuthorizedResource = mock(JwtAuthorizedResource.class);
        String issuer = "https://issuer.example.com";
        when(jwtAuthorizedResource.getIssuer()).thenReturn(issuer);
        JwksDecorator decorator = new JwksDecorator(jwtAuthorizedResource);
        decorator.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // Act
        HttpEventResponse response = decorator.handle(request);

        // Assert
        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        String expectedJson = String.format("{" +
            "\"issuer\":\"%s\"," +
            "\"jwks_uri\":\"%s/.well-known/jwks.json\"}", issuer, issuer);
        // Remove whitespace for comparison
        assertEquals(expectedJson.replaceAll("\\s+", ""), response.getBody().replaceAll("\\s+", ""));
    }
}
