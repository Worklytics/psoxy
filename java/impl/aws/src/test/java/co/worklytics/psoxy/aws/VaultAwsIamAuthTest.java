package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
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

    @Test
    void payloadEncoded() {
        VaultAwsIamAuth vaultAwsIamAuth = new VaultAwsIamAuth(
            "us-east-1",
            DefaultAWSCredentialsProviderChain.getInstance().getCredentials()
        );
        vaultAwsIamAuth.envVarsConfigService = new EnvVarsConfigService();
        vaultAwsIamAuth.objectMapper = new ObjectMapper();
        String payload = vaultAwsIamAuth.getPayload();
        assertEquals("QWN0aW9uPUdldENhbGxlcklkZW50aXR5JlZlcnNpb249MjAxMS0wNi0xNQ==",
            Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void endpointEncoded() {
        VaultAwsIamAuth vaultAwsIamAuth = new VaultAwsIamAuth(
            "us-east-1",
            DefaultAWSCredentialsProviderChain.getInstance().getCredentials()
        );
        vaultAwsIamAuth.envVarsConfigService = new EnvVarsConfigService();
        vaultAwsIamAuth.objectMapper = new ObjectMapper();
        String endpoint = vaultAwsIamAuth.getEndpoint();
        assertEquals("aHR0cHM6Ly9zdHMuYW1hem9uYXdzLmNvbQ==",
            Base64.getEncoder().encodeToString(endpoint.getBytes(StandardCharsets.UTF_8)));
    }

    //provide these values in CI env to run this test
    @Test
    void buildRequestHeaders() {
        String vaultServer = "http://localhost:8200";

        VaultAwsIamAuth vaultAwsIamAuth = new VaultAwsIamAuth(
            "us-east-1",
            new BasicAWSCredentials("access_key_id", "secret_key_id")
        );
        vaultAwsIamAuth.objectMapper = new ObjectMapper();
        vaultAwsIamAuth.envVarsConfigService = new EnvVarsConfigService();
        Map<String, String> headers = vaultAwsIamAuth.buildRequestHeaders(vaultServer);

        assertTrue(headers.containsKey("X-Vault-AWS-IAM-Server-ID"));
        assertTrue(headers.containsKey(AUTHORIZATION));
    }

    @Test
    void curlExample() {
        String vaultServer = "http://localhost:8200";

        VaultAwsIamAuth vaultAwsIamAuth = new VaultAwsIamAuth(
            "us-east-1",
            new BasicAWSCredentials("access_key_id", "secret_key_id")
        );
        vaultAwsIamAuth.objectMapper = new ObjectMapper();
        vaultAwsIamAuth.envVarsConfigService = new EnvVarsConfigService();
        Map<String, String> headers = vaultAwsIamAuth.buildRequestHeaders(vaultServer);

        assertTrue(headers.containsKey("X-Vault-AWS-IAM-Server-ID"));
        assertTrue(headers.containsKey(AUTHORIZATION));
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
        Map<String, String> headers = vaultAwsIamAuth.buildRequestHeaders(vaultServer);

        //assertTrue(headers.containsKey("X-Vault-AWS-IAM-Server-ID"));
        assertTrue(headers.containsKey(AUTHORIZATION));

        String asCurl = "curl -X POST -D \"" + vaultAwsIamAuth.getPayload() + "\" \\\n";
        String headerString = headers.entrySet().stream()
            .map(entry ->  "-H \"" + entry.getKey() + ": " + entry.getValue() + "\"")
            .collect(Collectors.joining(" \\\n"));

        asCurl += headerString;
        asCurl += " " + vaultAwsIamAuth.getEndpoint();



        final JsonObject jsonObject = new JsonObject();
        headers.forEach((k, v) -> {
            final JsonArray array = new JsonArray();
            array.add((String) v);
            jsonObject.add((String) k, array);
        });


        String serialized = jsonObject.toString();

        vaultAwsIamAuth.getPayload();
    }
}
