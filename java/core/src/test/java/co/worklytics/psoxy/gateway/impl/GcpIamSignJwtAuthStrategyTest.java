package co.worklytics.psoxy.gateway.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.Credentials;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.AwsCredentials;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.oauth.AuthUtils;
import co.worklytics.test.MockModules;

class GcpIamSignJwtAuthStrategyTest {

    static final Instant NOW = Instant.parse("2026-09-19T15:00:00Z");
    static final String DWD_SA = "dwd-connector@project.iam.gserviceaccount.com";
    static final String USER = "admin@example.com";
    static final String SCOPES = "https://www.googleapis.com/auth/admin.directory.user.readonly";
    static final String SIGNED_JWT = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.e30.sig";
    static final String ACCESS_TOKEN = "ya29.delegated-token";

    static final String SIGN_JWT_RESPONSE = "{\"keyId\":\"abc\",\"signedJwt\":\"" + SIGNED_JWT + "\"}";
    static final String TOKEN_RESPONSE =
        "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}";

    ObjectMapper objectMapper = new ObjectMapper();
    ConfigService config;
    RecordingTransport recordingTransport;
    GcpIamSignJwtAuthStrategy strategy;

    @BeforeEach
    void setup() {
        config = MockModules.provideMock(ConfigService.class);
        when(config.getConfigPropertyOrError(GcpIamSignJwtAuthStrategy.ConfigProperty.SERVICE_ACCOUNT_EMAIL))
            .thenReturn(DWD_SA);
        when(config.getConfigPropertyOrError(GcpIamSignJwtAuthStrategy.ConfigProperty.OAUTH_SCOPES))
            .thenReturn(SCOPES);
        when(config.getConfigPropertyOrError(GcpIamSignJwtAuthStrategy.ConfigProperty.PROCESS_IDENTITY_SOURCE))
            .thenReturn("test");

        recordingTransport = new RecordingTransport();
        HttpTransportFactory httpTransportFactory = () -> recordingTransport;

        GoogleCloudProcessIdentity testIdentity = new GoogleCloudProcessIdentity() {
            @Override
            public String getConfigIdentifier() {
                return "test";
            }

            @Override
            public GoogleCredentials getCredentials() {
                // expiration is evaluated against wall-clock inside google-auth-library, not our
                // injected Clock used for JWT iat/exp
                return GoogleCredentials.create(
                    new AccessToken("process-token", Date.from(Instant.parse("2099-01-01T00:00:00Z"))));
            }

            @Override
            public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
                return Set.of();
            }

            @Override
            public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
                return Set.of();
            }
        };

        strategy = new GcpIamSignJwtAuthStrategy();
        strategy.config = config;
        strategy.httpTransportFactory = httpTransportFactory;
        strategy.objectMapper = objectMapper;
        strategy.clock = Clock.fixed(NOW, ZoneOffset.UTC);
        strategy.processIdentities = Set.of(testIdentity);
    }

    @Test
    void configIdentifier() {
        assertEquals("gcp_iam_sign_jwt", strategy.getConfigIdentifier());
    }

    @Test
    void buildJwtPayload_includesSubForDelegatedUser() throws Exception {
        String payload = strategy.buildJwtPayload(DWD_SA, USER);
        JsonNode node = objectMapper.readTree(payload);

        assertEquals(DWD_SA, node.get("iss").asText());
        assertEquals(USER, node.get("sub").asText());
        assertEquals(SCOPES, node.get("scope").asText());
        assertEquals("https://oauth2.googleapis.com/token", node.get("aud").asText());
        assertEquals(NOW.getEpochSecond(), node.get("iat").asLong());
        assertEquals(NOW.getEpochSecond() + 3600, node.get("exp").asLong());
    }

    @Test
    void buildJwtPayload_omitsSubWhenNoUser() throws Exception {
        String payload = strategy.buildJwtPayload(DWD_SA, null);
        JsonNode node = objectMapper.readTree(payload);

        assertFalse(node.has("sub"));
        assertEquals(DWD_SA, node.get("iss").asText());
    }

    @Test
    void getCredentials_signJwtThenJwtBearerExchange() throws Exception {
        Credentials credentials = strategy.getCredentials(Optional.of(USER));
        assertEquals("{Authorization=[Bearer " + ACCESS_TOKEN + "]}",
            credentials.getRequestMetadata().toString());

        assertEquals(2, recordingTransport.requests.size());
        RecordedRequest signJwt = recordingTransport.requests.get(0);
        RecordedRequest tokenExchange = recordingTransport.requests.get(1);

        assertTrue(signJwt.url.contains("iamcredentials.googleapis.com"));
        assertTrue(signJwt.url.contains(DWD_SA + ":signJwt"));
        JsonNode signJwtBody = objectMapper.readTree(signJwt.content);
        JsonNode jwtPayload = objectMapper.readTree(signJwtBody.get("payload").asText());
        assertEquals(USER, jwtPayload.get("sub").asText());
        assertEquals(DWD_SA, jwtPayload.get("iss").asText());

        assertEquals("https://oauth2.googleapis.com/token", tokenExchange.url);
        assertTrue(tokenExchange.content.contains("grant_type="));
        String encodedGrantType = AuthUtils.JWT_BEARER_GRANT_TYPE.replace(":", "%3A");
        assertTrue(tokenExchange.content.contains(encodedGrantType)
            || tokenExchange.content.contains(AuthUtils.JWT_BEARER_GRANT_TYPE));
        assertTrue(tokenExchange.content.contains("assertion=" + SIGNED_JWT)
            || tokenExchange.content.contains("assertion=" + SIGNED_JWT.replace(".", "%2E")));
    }

    @Test
    void getRequiredConfigProperties_includesProcessIdentityWhenConfigured() {
        AwsWifGoogleCloudProcessIdentity awsWif = new AwsWifGoogleCloudProcessIdentity();
        strategy.processIdentities = Set.of(awsWif);
        when(config.getConfigPropertyOrError(GcpIamSignJwtAuthStrategy.ConfigProperty.PROCESS_IDENTITY_SOURCE))
            .thenReturn(AwsWifGoogleCloudProcessIdentity.CONFIG_IDENTIFIER);

        Set<ConfigService.ConfigProperty> required = strategy.getRequiredConfigProperties();
        assertTrue(required.contains(GcpIamSignJwtAuthStrategy.ConfigProperty.SERVICE_ACCOUNT_EMAIL));
        assertTrue(required.contains(GcpIamSignJwtAuthStrategy.ConfigProperty.OAUTH_SCOPES));
        assertTrue(required.contains(GcpIamSignJwtAuthStrategy.ConfigProperty.PROCESS_IDENTITY_SOURCE));
        assertTrue(required.contains(AwsWifGoogleCloudProcessIdentity.ConfigProperty.GCP_WIF_AUDIENCE));
    }

    @Test
    void getProcessIdentity_unknownIdentifier() {
        when(config.getConfigPropertyOrError(GcpIamSignJwtAuthStrategy.ConfigProperty.PROCESS_IDENTITY_SOURCE))
            .thenReturn("not-a-real-identity");
        assertThrows(IllegalStateException.class, strategy::getProcessIdentity);
    }

    @Test
    void gcpHostedProcessIdentity_isComputeEngineCredentialsNotAdc() {
        GcpHostedProcessIdentity identity = new GcpHostedProcessIdentity();
        identity.httpTransportFactory = () -> recordingTransport;
        assertEquals(GcpHostedProcessIdentity.CONFIG_IDENTIFIER, identity.getConfigIdentifier());
        assertInstanceOf(ComputeEngineCredentials.class, identity.getCredentials());
    }

    @Test
    void awsWifProcessIdentity_isAwsCredentialsNotAdc() {
        ConfigService wifConfig = MockModules.provideMock(ConfigService.class);
        when(wifConfig.getConfigPropertyOrError(AwsWifGoogleCloudProcessIdentity.ConfigProperty.GCP_WIF_AUDIENCE))
            .thenReturn("//iam.googleapis.com/projects/123/locations/global/workloadIdentityPools/pool/providers/aws");
        when(wifConfig.getConfigPropertyAsOptional(AwsWifGoogleCloudProcessIdentity.ConfigProperty.GCP_WIF_TOKEN_URL))
            .thenReturn(Optional.empty());
        when(wifConfig.getConfigPropertyAsOptional(
            AwsWifGoogleCloudProcessIdentity.ConfigProperty.GCP_WIF_SERVICE_ACCOUNT_IMPERSONATION_URL))
            .thenReturn(Optional.empty());

        AwsWifGoogleCloudProcessIdentity identity = new AwsWifGoogleCloudProcessIdentity();
        identity.config = wifConfig;
        identity.httpTransportFactory = () -> recordingTransport;

        assertEquals(AwsWifGoogleCloudProcessIdentity.CONFIG_IDENTIFIER, identity.getConfigIdentifier());
        assertInstanceOf(AwsCredentials.class, identity.getCredentials());
    }

    @Test
    void isSourceAuthFailure_iamAndTokenEndpoints() {
        assertTrue(strategy.isSourceAuthFailure(new IOException(
            "POST https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/sa:signJwt 403")));
        assertTrue(strategy.isSourceAuthFailure(new IOException(
            "Error getting access token for service account: POST https://oauth2.googleapis.com/token")));
        assertFalse(strategy.isSourceAuthFailure(new IOException("unexpected end of stream")));
    }

    static final class RecordedRequest {
        final String url;
        final String content;

        RecordedRequest(String url, String content) {
            this.url = url;
            this.content = content;
        }
    }

    static final class RecordingTransport extends MockHttpTransport {
        final List<RecordedRequest> requests = new ArrayList<>();

        @Override
        public LowLevelHttpRequest buildRequest(String method, String url) {
            return new MockLowLevelHttpRequest(url) {
                @Override
                public LowLevelHttpResponse execute() throws IOException {
                    requests.add(new RecordedRequest(url, getContentAsString()));
                    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse().setStatusCode(200);
                    if (url.contains(":signJwt")) {
                        response.setContent(SIGN_JWT_RESPONSE);
                    } else {
                        response.setContent(TOKEN_RESPONSE);
                    }
                    return response;
                }
            };
        }
    }
}
