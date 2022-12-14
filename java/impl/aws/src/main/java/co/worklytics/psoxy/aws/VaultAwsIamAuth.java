package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.AWSConfigProperty;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.VaultConfigService;
import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;
import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.response.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;


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
@Log
@NoArgsConstructor(onConstructor_ = @Inject)
public class VaultAwsIamAuth {

    @Inject
    EnvVarsConfigService envVarsConfigService;
    @Inject
    ObjectMapper objectMapper;

    @AssistedInject
    VaultAwsIamAuth(@Assisted @NonNull String awsRegion,
                    @Assisted @NonNull AWSCredentials awsCredentials) {
        this.region = awsRegion;
        this.credentials = awsCredentials;
    }

    static final String SERVER_ID_HEADER = "X-Vault-AWS-IAM-Server-ID";

    @Getter
    String region;

    @Getter
    AWSCredentials credentials;

    @Getter
    final String payload = "Action=GetCallerIdentity&Version=2011-06-15";


    @SneakyThrows
    Map<String, String> buildRequestHeaders(@NonNull String url) {

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(SERVER_ID_HEADER, url.split("//")[1]);
        //headers.put("User-Agent", "Psoxy/0.4.9");

        AWSSecurityTokenService awsSecurityTokenService = AWSSecurityTokenServiceClient.builder()
            .withRegion(region)
            .build();

        if (envVarsConfigService.isDevelopment()) {

            com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest request =
                new com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest();
            GetCallerIdentityResult r = awsSecurityTokenService.getCallerIdentity(request);

            //result is something like
            // arn:aws:sts::{{YOUR_AWS_ACCOUNT_ID}}:assumed-role/PsoxyExec_psoxy-gcal/psoxy-gcal
            log.info("getCallerIdentity: " + r.getArn());
        }


        DefaultRequest defaultRequest = new DefaultRequest("sts");
        defaultRequest.setHttpMethod(HttpMethodName.POST);
        defaultRequest.setEndpoint(new URI(getEndpoint()));
        defaultRequest.setHeaders(headers);


        defaultRequest.setContent(new ByteArrayInputStream(getPayload().getBytes(StandardCharsets.UTF_8)));

        AWS4Signer aws4Signer = new AWS4Signer();
        aws4Signer.setServiceName(defaultRequest.getServiceName());
        aws4Signer.setRegionName(getRegion());

        //it appears that a 'X-Amz-Security-Token' header is being added by this, presumably bc
        // these are AWSSessionCredentials when invoked from a lambda
        // not only that, this header is part of the headers that end up being SIGNED
        // Vault some vault examples LACK this header, but C# example has it ...
        aws4Signer.sign(defaultRequest, getCredentials());

        // from example https://docs.aws.amazon.com/STS/latest/APIReference/API_GetCallerIdentity.html,
        // these headers are included but not signed
        // but in example in Vault docs, headers are included AND signed
        defaultRequest.addHeader("Accept-Encoding", "identity");
        // vault example includes charset, but not in AWS docs don't
        defaultRequest.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        //w/o Content-Length, trying this over curl gives 302; both AWS and vault examples include it
        defaultRequest.addHeader("Content-Length", String.valueOf(payload.getBytes(StandardCharsets.UTF_8).length));

        return defaultRequest.getHeaders();
    }


    @VisibleForTesting
    String getEndpoint() {
        return "https://sts.amazonaws.com";
        //seen examples with this alternative
        //return String.format("https://sts.%s.amazonaws.com", getRegion());
    }


    @SneakyThrows
    String getToken() {
        String vaultAddress =
            envVarsConfigService.getConfigPropertyOrError(VaultConfigService.VaultConfigProperty.VAULT_ADDR);
        VaultConfig vaultConfig = new VaultConfig()
            .sslConfig(new SslConfig().build())
            .address(vaultAddress);
        Vault authVault = new Vault(vaultConfig);

        String role =
            envVarsConfigService.getConfigPropertyAsOptional(VaultConfigService.VaultConfigProperty.VAULT_ROLE)
                .orElseGet(() -> System.getenv(AwsModule.RuntimeEnvironmentVariables.AWS_LAMBDA_FUNCTION_NAME.name()));

        log.info("Vault Auth : aws iam with role:" + role);

        String serializedHeaders = serializeToJsonMultimap(buildRequestHeaders(vaultAddress));
        //serializedHeaders = objectMapper.writeValueAsString(buildRequestHeaders(vaultAddress));

        log.info(serializedHeaders);

        // in effect, you're building AWS STS request but not sending it to AWS directly; instead,
        // you send it to Vault to try - and if works for Vault, Vault will return an auth token
        AuthResponse authResponse = authVault.auth()
            .loginByAwsIam(
                role, //pretty sure this value needs to match the last segment of the 'role' set in Vault
                // eg vault write auth/aws/role/psoxy-gcal auth_type=iam policies={{YOUR_VAULT_POLICY}} max_ttl=500h bound_iam_principal_arn={{EXECUTION_ROLE_ARN}}
                //  then --> psoxy-gcal
                Base64.getEncoder().encodeToString(getEndpoint().getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString(getPayload().getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString(serializedHeaders.getBytes(StandardCharsets.UTF_8)),
                "aws"
            );

        //above gives 403
        // ideas
        //   - something misconfigured in Vault?
        //   - AWS STS call fails?


        return authResponse.getAuthClientToken();
    }

    // results in JSON payload that is most similar to Vault aws auth example
    // *seems* like this is closer to working? think error message from vault has changed; still 403,
    // but possibly is a 403 due to vault role configurations for AWS auth, rather than the AWS STS
    // call failing
    String serializeToJsonMultimap(Map<String, String> map) {
        final JsonObject jsonObject = new JsonObject();
        map.forEach((k, v) -> {
            final JsonArray array = new JsonArray();
            array.add((String) v);
            jsonObject.add((String) k, array);
        });
        return jsonObject.toString();
    }
}
