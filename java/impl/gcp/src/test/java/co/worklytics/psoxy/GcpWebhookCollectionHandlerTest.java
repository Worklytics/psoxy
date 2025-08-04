package co.worklytics.psoxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import co.worklytics.psoxy.GcpWebhookCollectionHandler.AuthorizationException;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import lombok.SneakyThrows;

@Disabled // Cursor-generated test doesn't really work ..
@ExtendWith(MockitoExtension.class)
class GcpWebhookCollectionHandlerTest {

    @Mock
    private HttpRequest mockRequest;
    
    @Mock
    private HttpResponse mockResponse;
    
    @Mock
    private ConfigService mockConfigService;
    
    @Mock
    private EnvVarsConfigService mockEnvVarsConfigService;
    
    @Mock
    private GoogleIdTokenVerifierFactory mockGoogleIdTokenVerifierFactory;
    
    @Mock
    private GoogleIdTokenVerifier mockGoogleIdTokenVerifier;
    
    @Mock
    private GcpEnvironment mockGcpEnvironment;
    
    @Mock
    private GoogleIdToken mockGoogleIdToken;
    
    @Mock
    private GoogleIdToken.Payload mockPayload;
    
    @Mock
    private JsonFactory mockJsonFactory;

    private GcpWebhookCollectionHandler handler;
    private static final String VALID_JWT = "valid.jwt.token";
    private static final String EXPECTED_ISSUER = "https://accounts.google.com";
    private static final String EXPECTED_AUDIENCE = "https://my-endpoint.com";

    @BeforeEach
    void setUp() {
        // Setup mocks
        when(mockGoogleIdTokenVerifierFactory.getVerifierForAudience(anyString()))
            .thenReturn(mockGoogleIdTokenVerifier);
        when(mockGoogleIdTokenVerifier.getJsonFactory())
            .thenReturn(new GsonFactory());
        when(mockConfigService.getConfigPropertyOrError(any()))
            .thenReturn(EXPECTED_AUDIENCE);
        when(mockGcpEnvironment.getInternalServiceAuthIssuer())
            .thenReturn(EXPECTED_ISSUER);
        when(mockEnvVarsConfigService.isDevelopment())
            .thenReturn(false);


        GcpEnvironment.WebhookCollectorModeConfig webhookCollectorModeConfig = GcpEnvironment.WebhookCollectorModeConfig.builder()
            .batchMergeSubscription("projects/my-project/subscriptions/my-subscription")
            .batchSize(100)
            .batchInvocationTimeoutSeconds(60)
            .build();

        // Create handler with mocked dependencies
        handler = new GcpWebhookCollectionHandler(
            null, // inboundWebhookHandler - not needed for these tests
            mockGoogleIdTokenVerifierFactory,
            mockGcpEnvironment,
            null, // batchMergeHandler - not needed for these tests
            null, // jwksDecoratorFactory - not needed for these tests
            mockConfigService,
            mockEnvVarsConfigService,
            () -> webhookCollectorModeConfig
        );
    }

    @Test
    void verifyAuthorization_NoAuthorizationHeader_ThrowsAuthorizationException() {
        // Arrange
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.empty());

        // Act & Assert
        AuthorizationException exception = assertThrows(
            AuthorizationException.class,
            () -> handler.verifyAuthorization(mockRequest)
        );
        
        assertEquals("Unauthorized : no authorization header included", exception.getMessage());
    }

    @Test
    void verifyAuthorization_EmptyAuthorizationHeader_ThrowsAuthorizationException() {
        // Arrange
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.of(""));

        // Act & Assert
        AuthorizationException exception = assertThrows(
            AuthorizationException.class,
            () -> handler.verifyAuthorization(mockRequest)
        );
        
        assertEquals("Unauthorized : no authorization header included", exception.getMessage());
    }

    @Test
    @SneakyThrows
    void verifyAuthorization_InvalidJwtFormat_ThrowsAuthorizationException() {
        // Arrange
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.of("Bearer invalid.jwt"));
        when(mockGoogleIdTokenVerifier.getJsonFactory())
            .thenReturn(mockJsonFactory);
        when(GoogleIdToken.parse(any(JsonFactory.class), anyString()))
            .thenThrow(new IllegalArgumentException("Invalid JWT format"));

        // Act & Assert
        AuthorizationException exception = assertThrows(
            AuthorizationException.class,
            () -> handler.verifyAuthorization(mockRequest)
        );
        
        assertEquals("Unauthorized : invalid JWT", exception.getMessage());
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    @SneakyThrows
    void verifyAuthorization_NullIdToken_ThrowsAuthorizationException() {
        // Arrange
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.of("Bearer " + VALID_JWT));
        when(mockGoogleIdTokenVerifier.getJsonFactory())
            .thenReturn(mockJsonFactory);
        when(GoogleIdToken.parse(any(JsonFactory.class), anyString()))
            .thenReturn(null);

        // Act & Assert
        AuthorizationException exception = assertThrows(
            AuthorizationException.class,
            () -> handler.verifyAuthorization(mockRequest)
        );
        
        assertEquals("Unauthorized - failed to parse JWT", exception.getMessage());
    }

    @Test
    @SneakyThrows
    void verifyAuthorization_WrongIssuer_ThrowsAuthorizationException() {
        // Arrange
        String wrongIssuer = "https://wrong-issuer.com";
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.of("Bearer " + VALID_JWT));
        when(mockGoogleIdTokenVerifier.getJsonFactory())
            .thenReturn(mockJsonFactory);
        when(GoogleIdToken.parse(any(JsonFactory.class), anyString()))
            .thenReturn(mockGoogleIdToken);
        when(mockGoogleIdToken.getPayload())
            .thenReturn(mockPayload);
        when(mockPayload.getIssuer())
            .thenReturn(wrongIssuer);

        // Act & Assert
        AuthorizationException exception = assertThrows(
            AuthorizationException.class,
            () -> handler.verifyAuthorization(mockRequest)
        );
        
        assertEquals("Unauthorized - unacceptable issuer", exception.getMessage());
    }

    @Test
    @SneakyThrows
    void verifyAuthorization_ExpiredToken_ThrowsAuthorizationException() {
        // Arrange
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.of("Bearer " + VALID_JWT));
        when(mockGoogleIdTokenVerifier.getJsonFactory())
            .thenReturn(mockJsonFactory);
        when(GoogleIdToken.parse(any(JsonFactory.class), anyString()))
            .thenReturn(mockGoogleIdToken);
        when(mockGoogleIdToken.getPayload())
            .thenReturn(mockPayload);
        when(mockPayload.getIssuer())
            .thenReturn(EXPECTED_ISSUER);
        
        // Mock expired token verification
        doThrow(new IllegalArgumentException("Token expired"))
            .when(mockGoogleIdTokenVerifier).verifyOrThrow(any(GoogleIdToken.class));

        // Act & Assert
        AuthorizationException exception = assertThrows(
            AuthorizationException.class,
            () -> handler.verifyAuthorization(mockRequest)
        );
        
        assertEquals("Unauthorized : invalid JWT", exception.getMessage());
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    @SneakyThrows
    void verifyAuthorization_InvalidSignature_ThrowsAuthorizationException() {
        // Arrange
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.of("Bearer " + VALID_JWT));
        when(mockGoogleIdTokenVerifier.getJsonFactory())
            .thenReturn(mockJsonFactory);
        when(GoogleIdToken.parse(any(JsonFactory.class), anyString()))
            .thenReturn(mockGoogleIdToken);
        when(mockGoogleIdToken.getPayload())
            .thenReturn(mockPayload);
        when(mockPayload.getIssuer())
            .thenReturn(EXPECTED_ISSUER);
        
        // Mock invalid signature verification
        doThrow(new IllegalArgumentException("Invalid signature"))
            .when(mockGoogleIdTokenVerifier).verifyOrThrow(any(GoogleIdToken.class));

        // Act & Assert
        AuthorizationException exception = assertThrows(
            AuthorizationException.class,
            () -> handler.verifyAuthorization(mockRequest)
        );
        
        assertEquals("Unauthorized : invalid JWT", exception.getMessage());
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    @SneakyThrows
    void verifyAuthorization_WrongAudience_ThrowsAuthorizationException() {
        // Arrange
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.of("Bearer " + VALID_JWT));
        when(mockGoogleIdTokenVerifier.getJsonFactory())
            .thenReturn(mockJsonFactory);
        when(GoogleIdToken.parse(any(JsonFactory.class), anyString()))
            .thenReturn(mockGoogleIdToken);
        when(mockGoogleIdToken.getPayload())
            .thenReturn(mockPayload);
        when(mockPayload.getIssuer())
            .thenReturn(EXPECTED_ISSUER);
        
        // Mock wrong audience verification (GoogleIdTokenVerifier checks audience internally)
        doThrow(new IllegalArgumentException("Wrong audience"))
            .when(mockGoogleIdTokenVerifier).verifyOrThrow(any(GoogleIdToken.class));

        // Act & Assert
        AuthorizationException exception = assertThrows(
            AuthorizationException.class,
            () -> handler.verifyAuthorization(mockRequest)
        );
        
        assertEquals("Unauthorized : invalid JWT", exception.getMessage());
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    @SneakyThrows
    void verifyAuthorization_ValidToken_Succeeds() {
        // Arrange
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.of("Bearer " + VALID_JWT));
        when(mockGoogleIdTokenVerifier.getJsonFactory())
            .thenReturn(mockJsonFactory);
        when(GoogleIdToken.parse(any(JsonFactory.class), anyString()))
            .thenReturn(mockGoogleIdToken);
        when(mockGoogleIdToken.getPayload())
            .thenReturn(mockPayload);
        when(mockPayload.getIssuer())
            .thenReturn(EXPECTED_ISSUER);
        
        // Mock successful verification
        doNothing().when(mockGoogleIdTokenVerifier).verifyOrThrow(any(GoogleIdToken.class));

        // Act & Assert
        assertDoesNotThrow(() -> handler.verifyAuthorization(mockRequest));
        
        // Verify the verification was called
        verify(mockGoogleIdTokenVerifier).verifyOrThrow(mockGoogleIdToken);
    }

    @Test
    @SneakyThrows
    void verifyAuthorization_DevelopmentMode_LogsAuthorizationHeader() {
        // Arrange
        when(mockEnvVarsConfigService.isDevelopment())
            .thenReturn(true);
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.of("Bearer " + VALID_JWT));
        when(mockGoogleIdTokenVerifier.getJsonFactory())
            .thenReturn(mockJsonFactory);
        when(GoogleIdToken.parse(any(JsonFactory.class), anyString()))
            .thenReturn(mockGoogleIdToken);
        when(mockGoogleIdToken.getPayload())
            .thenReturn(mockPayload);
        when(mockPayload.getIssuer())
            .thenReturn(EXPECTED_ISSUER);
        doNothing().when(mockGoogleIdTokenVerifier).verifyOrThrow(any(GoogleIdToken.class));

        // Act
        handler.verifyAuthorization(mockRequest);

        // Assert - verify development mode logging (this would require a more sophisticated test setup
        // with a real logger, but we can verify the method doesn't throw)
        assertDoesNotThrow(() -> handler.verifyAuthorization(mockRequest));
    }

    @Test
    @SneakyThrows
    void verifyAuthorization_IOException_ThrowsAuthorizationException() {
        // Arrange
        when(mockRequest.getFirstHeader("Authorization"))
            .thenReturn(Optional.of("Bearer " + VALID_JWT));
        when(mockGoogleIdTokenVerifier.getJsonFactory())
            .thenReturn(mockJsonFactory);
        when(GoogleIdToken.parse(any(JsonFactory.class), anyString()))
            .thenThrow(new IOException("Network error"));

        // Act & Assert
        AuthorizationException exception = assertThrows(
            AuthorizationException.class,
            () -> handler.verifyAuthorization(mockRequest)
        );
        
        assertEquals("Unauthorized : invalid JWT", exception.getMessage());
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof IOException);
    }
} 