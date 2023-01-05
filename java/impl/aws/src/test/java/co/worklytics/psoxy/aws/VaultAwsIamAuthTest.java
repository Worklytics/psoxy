package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import static com.amazonaws.auth.internal.SignerConstants.AUTHORIZATION;
import static org.junit.jupiter.api.Assertions.*;


class VaultAwsIamAuthTest {

    //these both taken directly from Vault documentation
    static final String BASE64_UTF8_ENCODED_ENDPOINT = "aHR0cHM6Ly9zdHMuYW1hem9uYXdzLmNvbQ==";
    static final String BASE64_UTF8_ENCODED_PAYLOAD = "QWN0aW9uPUdldENhbGxlcklkZW50aXR5JlZlcnNpb249MjAxMS0wNi0xNQ==";

    @Test
    void payloadEncoded() {
        VaultAwsIamAuth vaultAwsIamAuth = new VaultAwsIamAuth(
            "us-east-1",
            new BasicAWSCredentials("access_key_id", "secret_key_id")
        );
        vaultAwsIamAuth.envVarsConfigService = new EnvVarsConfigService();
        vaultAwsIamAuth.objectMapper = new ObjectMapper();
        String payload = vaultAwsIamAuth.getPayload();
        assertEquals(BASE64_UTF8_ENCODED_PAYLOAD,
            Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void endpointEncoded() {

        String vaultServer = "http://localhost:8200";
        VaultAwsIamAuth vaultAwsIamAuth = new VaultAwsIamAuth(
            "us-east-1",
            new BasicAWSCredentials("access_key_id", "secret_key_id")
        );
        vaultAwsIamAuth.envVarsConfigService = new EnvVarsConfigService();
        vaultAwsIamAuth.objectMapper = new ObjectMapper();
        assertEquals(BASE64_UTF8_ENCODED_ENDPOINT,
            Base64.getEncoder().encodeToString(vaultAwsIamAuth.getEndpoint().getBytes(StandardCharsets.UTF_8)));
    }

    //provide these values in CI env to run this test
    @SneakyThrows
    @Test
    void buildGetCallerIdentityRequest() {
        String vaultServer = "http://localhost:8200";

        VaultAwsIamAuth vaultAwsIamAuth = new VaultAwsIamAuth(
            "us-east-1",
            new BasicAWSCredentials("access_key_id", "secret_key_id")
        );
        vaultAwsIamAuth.objectMapper = new ObjectMapper();
        vaultAwsIamAuth.envVarsConfigService = new EnvVarsConfigService();
        DefaultRequest request = vaultAwsIamAuth.buildGetCallerIdentityRequest(vaultServer);

        assertTrue(request.getHeaders().containsKey("X-Vault-AWS-IAM-Server-ID"));
        assertTrue(request.getHeaders().containsKey(AUTHORIZATION));

        assertEquals(BASE64_UTF8_ENCODED_ENDPOINT,
            Base64.getEncoder().encodeToString(request.getEndpoint().toString().getBytes(StandardCharsets.UTF_8)));

        assertEquals(BASE64_UTF8_ENCODED_PAYLOAD,
            Base64.getEncoder().encodeToString(IOUtils.toByteArray(request.getContent())));
    }

    @Test
    void curlExample() {
        String vaultServer = "http://localhost";

        VaultAwsIamAuth vaultAwsIamAuth = new VaultAwsIamAuth(
            "us-east-1",
            new BasicAWSCredentials("access_key_id", "secret_key_id")
        );
        vaultAwsIamAuth.objectMapper = new ObjectMapper();
        vaultAwsIamAuth.envVarsConfigService = new EnvVarsConfigService();

        DefaultRequest request = vaultAwsIamAuth.buildGetCallerIdentityRequest(vaultServer);

        assertTrue(request.getHeaders().containsKey("X-Vault-AWS-IAM-Server-ID"));
        assertTrue(request.getHeaders().containsKey(AUTHORIZATION));
    }


    //provide these values in CI env to run this test
    @SneakyThrows
    @EnabledIfEnvironmentVariable(named = "VAULT_ADDR", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
    @Test
    void buildRequestHeaders_curl() {
        String vaultServer = System.getenv("VAULT_ADDR");

        VaultAwsIamAuth vaultAwsIamAuth = new VaultAwsIamAuth(
            "us-east-1",
            DefaultAWSCredentialsProviderChain.getInstance().getCredentials()
        );
        vaultAwsIamAuth.objectMapper = new ObjectMapper();
        vaultAwsIamAuth.envVarsConfigService = new EnvVarsConfigService();
        DefaultRequest request = vaultAwsIamAuth.buildGetCallerIdentityRequest(vaultServer);

        //assertTrue(headers.containsKey("X-Vault-AWS-IAM-Server-ID"));
        assertTrue(request.getHeaders().containsKey(AUTHORIZATION));

        final JsonObject jsonObject = new JsonObject();
        request.getHeaders().forEach((k, v) -> {
            final JsonArray array = new JsonArray();
            array.add((String) v);
            jsonObject.add((String) k, array);
        });


        String serialized = jsonObject.toString();

        vaultAwsIamAuth.getPayload();
    }
}
