package co.worklytics.psoxy.gateway.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.test.MockModules;
import dagger.Component;
import lombok.SneakyThrows;

class GoogleCloudPlatformServiceAccountKeyAuthStrategyTest {

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        MockModules.ForConfigService.class,
        MockModules.ForHttpTransportFactory.class,
        MockModules.ForSecretStore.class,
    })
    public interface Container {
        void inject(GoogleCloudPlatformServiceAccountKeyAuthStrategyTest test);
    }

    //a FAKE key!!!!
    // - need to use something that *appears* to be a real key here, so that all the precondition
    //   checks in GoogleCredential library pass
    public final String KEY = "{\n" +
        "  \"type\": \"service_account\",\n" +
        "  \"private_key_id\": \"abc\",\n" +
        "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDY3E8o1NEFcjMM\\nHW/5ZfFJw29/8NEqpViNjQIx95Xx5KDtJ+nWn9+OW0uqsSqKlKGhAdAo+Q6bjx2c\\nuXVsXTu7XrZUY5Kltvj94DvUa1wjNXs606r/RxWTJ58bfdC+gLLxBfGnB6CwK0YQ\\nxnfpjNbkUfVVzO0MQD7UP0Hl5ZcY0Puvxd/yHuONQn/rIAieTHH1pqgW+zrH/y3c\\n59IGThC9PPtugI9ea8RSnVj3PWz1bX2UkCDpy9IRh9LzJLaYYX9RUd7++dULUlat\\nAaXBh1U6emUDzhrIsgApjDVtimOPbmQWmX1S60mqQikRpVYZ8u+NDD+LNw+/Eovn\\nxCj2Y3z1AgMBAAECggEAWDBzoqO1IvVXjBA2lqId10T6hXmN3j1ifyH+aAqK+FVl\\nGjyWjDj0xWQcJ9ync7bQ6fSeTeNGzP0M6kzDU1+w6FgyZqwdmXWI2VmEizRjwk+/\\n/uLQUcL7I55Dxn7KUoZs/rZPmQDxmGLoue60Gg6z3yLzVcKiDc7cnhzhdBgDc8vd\\nQorNAlqGPRnm3EqKQ6VQp6fyQmCAxrr45kspRXNLddat3AMsuqImDkqGKBmF3Q1y\\nxWGe81LphUiRqvqbyUlh6cdSZ8pLBpc9m0c3qWPKs9paqBIvgUPlvOZMqec6x4S6\\nChbdkkTRLnbsRr0Yg/nDeEPlkhRBhasXpxpMUBgPywKBgQDs2axNkFjbU94uXvd5\\nznUhDVxPFBuxyUHtsJNqW4p/ujLNimGet5E/YthCnQeC2P3Ym7c3fiz68amM6hiA\\nOnW7HYPZ+jKFnefpAtjyOOs46AkftEg07T9XjwWNPt8+8l0DYawPoJgbM5iE0L2O\\nx8TU1Vs4mXc+ql9F90GzI0x3VwKBgQDqZOOqWw3hTnNT07Ixqnmd3dugV9S7eW6o\\nU9OoUgJB4rYTpG+yFqNqbRT8bkx37iKBMEReppqonOqGm4wtuRR6LSLlgcIU9Iwx\\nyfH12UWqVmFSHsgZFqM/cK3wGev38h1WBIOx3/djKn7BdlKVh8kWyx6uC8bmV+E6\\nOoK0vJD6kwKBgHAySOnROBZlqzkiKW8c+uU2VATtzJSydrWm0J4wUPJifNBa/hVW\\ndcqmAzXC9xznt5AVa3wxHBOfyKaE+ig8CSsjNyNZ3vbmr0X04FoV1m91k2TeXNod\\njMTobkPThaNm4eLJMN2SQJuaHGTGERWC0l3T18t+/zrDMDCPiSLX1NAvAoGBAN1T\\nVLJYdjvIMxf1bm59VYcepbK7HLHFkRq6xMJMZbtG0ryraZjUzYvB4q4VjHk2UDiC\\nlhx13tXWDZH7MJtABzjyg+AI7XWSEQs2cBXACos0M4Myc6lU+eL+iA+OuoUOhmrh\\nqmT8YYGu76/IBWUSqWuvcpHPpwl7871i4Ga/I3qnAoGBANNkKAcMoeAbJQK7a/Rn\\nwPEJB+dPgNDIaboAsh1nZhVhN5cvdvCWuEYgOGCPQLYQF0zmTLcM+sVxOYgfy8mV\\nfbNgPgsP5xmu6dw2COBKdtozw0HrWSRjACd1N4yGu75+wPCcX/gQarcjRcXXZeEa\\nNtBLSfcqPULqD+h7br9lEJio\\n-----END PRIVATE KEY-----\\n\",\n" +
        "  \"client_email\": \"123-abc@developer.gserviceaccount.com\",\n" +
        "  \"client_id\": \"123-abc.apps.googleusercontent.com\",\n" +
        "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
        "  \"token_uri\": \"http://localhost:8080/token\"\n" +
        "}";

    public final String FAKE_TOKEN_RESPONSE = "{\n" +
        "  \"access_token\": \"1/fFAGRNJru1FTz70BzhT3Zg\",\n" +
        "  \"expires_in\": 3920,\n" +
        "  \"token_type\": \"Bearer\",\n" +
        "  \"scope\": \"https://www.googleapis.com/auth/drive.metadata.readonly\",\n" +
        "  \"refresh_token\": \"1//xEoDL4iW3cxlI7yDbSRFYNG01kVKM2C-259HOF2aQbI\"\n" +
        "}";


    @Inject
    GoogleCloudPlatformServiceAccountKeyAuthStrategy authStrategy;

    @BeforeEach
    public void setup() {
        GoogleCloudPlatformServiceAccountKeyAuthStrategyTest.Container container = DaggerGoogleCloudPlatformServiceAccountKeyAuthStrategyTest_Container.create();
        container.inject(this);
    }

    @SneakyThrows
    @ValueSource(strings = {
        "single",
        "c,s,v",
        "space delimited"

    })
    @ParameterizedTest
    void getCredentials(String oauthScopes) {

        MockModules.ForHttpTransportFactory.mockResponse(authStrategy.httpTransportFactory, FAKE_TOKEN_RESPONSE);

        when(authStrategy.config.getConfigPropertyOrError(GoogleCloudPlatformServiceAccountKeyAuthStrategy.ConfigProperty.OAUTH_SCOPES))
            .thenReturn(oauthScopes);
        when(authStrategy.secretStore.getConfigPropertyAsOptional(GoogleCloudPlatformServiceAccountKeyAuthStrategy.ConfigProperty.SERVICE_ACCOUNT_KEY))
            .thenReturn(Optional.of(new String(Base64.getEncoder().encode(KEY.getBytes()))));


        Credentials credentials = authStrategy.getCredentials(Optional.empty());

        assertEquals("123-abc.apps.googleusercontent.com", ((ServiceAccountCredentials) credentials).getClientId());
        assertEquals("http://localhost:8080/token", ((ServiceAccountCredentials) credentials).getTokenServerUri().toString());
        Objects.equals(oauthScopes.replace(",", " "),
            ((ServiceAccountCredentials) credentials).getScopes().stream().collect(Collectors.joining(" ")));

        //exchanges for a Bearer token
        // NOTE: calling 'getRequestMetadata()' triggers a call to the token server
        assertEquals("{Authorization=[Bearer 1/fFAGRNJru1FTz70BzhT3Zg]}",
            credentials.getRequestMetadata().toString());
    }

    @ValueSource(strings = {
        //various cases of extra whitepsace added around base64-encoded value
        // seen in cases where customers copy-paste encoded keys into console
        " c29tZXRoaW5n",
        "c29tZXRoaW5n\n",
        "  c29tZXRoaW5n\n",
        "  c29tZXRoaW5n  \r\n",
    })
    @ParameterizedTest
    void toStream_extraWhitespace(String validBase64) {

        //confirm that test case would indeed fail legacy implementation
        assertThrows(IllegalArgumentException.class,
            () -> new ByteArrayInputStream(Base64.getDecoder().decode(validBase64)));

        //current implementation works as expected, even w the whitespace
        assertEquals("something",
            new String(authStrategy.toStream(validBase64).readAllBytes()));
    }

    @SneakyThrows
    @org.junit.jupiter.api.Test
    void toStream_plainJsonFormat() {
        // Test that plain JSON (UTF-8) format works
        ByteArrayInputStream stream = authStrategy.toStream(KEY);
        String result = new String(stream.readAllBytes());
        
        // Verify the result is valid JSON by checking key fields
        assertTrue(result.contains("\"type\": \"service_account\""));
        assertTrue(result.contains("\"client_email\": \"123-abc@developer.gserviceaccount.com\""));
        assertTrue(result.contains("\"private_key_id\": \"abc\""));
    }

    @SneakyThrows
    @org.junit.jupiter.api.Test
    void toStream_base64EncodedJsonFormat() {
        // Test that base64-encoded JSON format works
        String base64Key = Base64.getEncoder().encodeToString(KEY.getBytes());
        ByteArrayInputStream stream = authStrategy.toStream(base64Key);
        String result = new String(stream.readAllBytes());
        
        // Verify the result is valid JSON by checking key fields
        assertTrue(result.contains("\"type\": \"service_account\""));
        assertTrue(result.contains("\"client_email\": \"123-abc@developer.gserviceaccount.com\""));
        assertTrue(result.contains("\"private_key_id\": \"abc\""));
    }

    @SneakyThrows
    @org.junit.jupiter.api.Test
    void toStream_bothFormatsProduceSameResult() {
        // Test that both plain JSON and base64-encoded formats produce the same result
        String base64Key = Base64.getEncoder().encodeToString(KEY.getBytes());
        
        ByteArrayInputStream plainStream = authStrategy.toStream(KEY);
        ByteArrayInputStream base64Stream = authStrategy.toStream(base64Key);
        
        String plainResult = new String(plainStream.readAllBytes());
        String base64Result = new String(base64Stream.readAllBytes());
        
        assertEquals(plainResult, base64Result);
    }

    @SneakyThrows
    @org.junit.jupiter.api.Test
    void toStream_plainJsonWithWhitespace() {
        // Test that plain JSON with extra whitespace works
        String keyWithWhitespace = "  " + KEY + "\n";
        ByteArrayInputStream stream = authStrategy.toStream(keyWithWhitespace);
        String result = new String(stream.readAllBytes());
        
        // Verify the result is valid JSON
        assertTrue(result.contains("\"type\": \"service_account\""));
        assertTrue(result.contains("\"client_email\": \"123-abc@developer.gserviceaccount.com\""));
    }

    @SneakyThrows
    @ValueSource(strings = {
        "single",
        "c,s,v",
        "space delimited"
    })
    @ParameterizedTest
    void getCredentials_plainJsonKey(String oauthScopes) {
        // Test that credentials work with plain JSON key format
        MockModules.ForHttpTransportFactory.mockResponse(authStrategy.httpTransportFactory, FAKE_TOKEN_RESPONSE);

        when(authStrategy.config.getConfigPropertyOrError(GoogleCloudPlatformServiceAccountKeyAuthStrategy.ConfigProperty.OAUTH_SCOPES))
            .thenReturn(oauthScopes);
        when(authStrategy.secretStore.getConfigPropertyAsOptional(GoogleCloudPlatformServiceAccountKeyAuthStrategy.ConfigProperty.SERVICE_ACCOUNT_KEY))
            .thenReturn(Optional.of(KEY)); // Plain JSON format

        Credentials credentials = authStrategy.getCredentials(Optional.empty());

        assertEquals("123-abc.apps.googleusercontent.com", ((ServiceAccountCredentials) credentials).getClientId());
        assertEquals("http://localhost:8080/token", ((ServiceAccountCredentials) credentials).getTokenServerUri().toString());
    }

}
