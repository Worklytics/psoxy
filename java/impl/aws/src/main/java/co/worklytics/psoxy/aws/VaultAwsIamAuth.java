package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.VaultConfigService;
import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.http.HttpMethodName;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.response.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;


//adapt https://gist.github.com/kalpit/5670ff0729277e764981008dec67864b

/**
 * AWS IAM authentication for Vault
 * see https://developer.hashicorp.com/vault/docs/auth/aws
 * see https://registry.terraform.io/modules/hashicorp/vault/aws/latest/examples/vault-iam-auth
 *
 * requirements to use this:
 *   - set
 *
 */
public class VaultAwsIamAuth {

    @Inject
    EnvVarsConfigService envVarsConfigService;
    @Inject
    ObjectMapper objectMapper;

    static final String SERVER_ID_HEADER = "X-Vault-AWS-IAM-Server-ID";


    static final String VAULT_AUTH_STRATEGY_ID = "aws";

    //q: part of a generat VaultAuthStrategy interface?
    public String getVaultAuthStrategyId() {
        return VAULT_AUTH_STRATEGY_ID;
    }


    @Getter
    String region = System.getenv(AwsModule.RuntimeEnvironmentVariables.AWS_REGION.name());

    @Getter
    final String payload = "Action=GetCallerIdentity&Version=2011-06-15";


    @SneakyThrows
    Map<String, String> buildRequestHeaders(@NonNull String url) {

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(SERVER_ID_HEADER, url.split("//")[1]);
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");

        final DefaultRequest defaultRequest = new DefaultRequest("sts");
        defaultRequest.setContent(new ByteArrayInputStream(getPayload().getBytes(StandardCharsets.UTF_8)));
        defaultRequest.setHeaders(headers);
        defaultRequest.setHttpMethod(HttpMethodName.POST);
        defaultRequest.setEndpoint(new URI(getEndpoint()));

        final AWS4Signer aws4Signer = new AWS4Signer();
        aws4Signer.setServiceName(defaultRequest.getServiceName());
        aws4Signer.setRegionName(getRegion());
        aws4Signer.sign(defaultRequest, new DefaultAWSCredentialsProviderChain().getCredentials());

        return defaultRequest.getHeaders();
    }


    private String getEndpoint() {
        return String.format("https://sts.%s.amazonaws.com", getRegion());
    }


    @SneakyThrows
    String getToken() {
        String vaultAddress =
            envVarsConfigService.getConfigPropertyOrError(VaultConfigService.VaultConfigProperty.VAULT_ADDR);
        VaultConfig vaultConfig = new VaultConfig()
            .address(vaultAddress);
        Vault authVault = new Vault(vaultConfig);

        //can we get
        String role =
            envVarsConfigService.getConfigPropertyOrError(VaultConfigService.VaultConfigProperty.VAULT_ROLE);

        AuthResponse authResponse = authVault.auth()
            .loginByAwsIam(
                role,
                Base64.getEncoder().encodeToString(getEndpoint().getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString(getPayload().getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(buildRequestHeaders(vaultAddress))),
                getVaultAuthStrategyId()
            );

        return authResponse.getAuthClientToken();
    }

}
