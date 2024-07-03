package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.HostEnvironment;
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
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.*;
import lombok.extern.java.Log;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
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
public class VaultAwsIamAuth {

    static final Integer VAULT_ENGINE_VERSION = 2;

    @Inject
    EnvVarsConfigService envVarsConfigService;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    HostEnvironment hostEnvironment;

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

    @Getter(AccessLevel.PACKAGE)
    final String payload = "Action=GetCallerIdentity&Version=2011-06-15";



    @SneakyThrows
    public String getToken() {
        String vaultAddress =
            envVarsConfigService.getConfigPropertyOrError(VaultConfigService.VaultConfigProperty.VAULT_ADDR);
        VaultConfig vaultConfig = new VaultConfig()
            .engineVersion(VAULT_ENGINE_VERSION)
            .sslConfig(new SslConfig().build())
            .address(vaultAddress);

        envVarsConfigService.getConfigPropertyAsOptional(VaultConfigService.VaultConfigProperty.VAULT_NAMESPACE)
            .filter(StringUtils::isNotBlank)  //don't bother tossing error here, assume meant no namespace
            .ifPresent(ns -> {
                try {
                    vaultConfig.nameSpace(ns);
                } catch (VaultException e) {
                    throw new Error("Error setting Vault namespace", e);
                }
            });

        Vault authVault = new Vault(vaultConfig);

        String role =
            envVarsConfigService.getConfigPropertyAsOptional(VaultConfigService.VaultConfigProperty.VAULT_ROLE)
                .orElseGet(hostEnvironment::getInstanceId);

        log.info("Vault Auth : aws iam with role:" + role);

        DefaultRequest prototypeRequest = buildGetCallerIdentityRequest(vaultAddress);
        String serializedHeaders = serializeToJsonMultimap(prototypeRequest.getHeaders());

        // authenticate with Vault server by sending it prototype AWS STS request, which it will
        // then send to AWS STS to establish caller's identity.
        // if this works, and the caller's identity is allowed to access the role, then Vault will
        // return a token for that role

        String iamRequestBody =
            Base64.getEncoder().encodeToString(IOUtils.toByteArray(prototypeRequest.getContent()));


        AuthResponse authResponse = authVault.auth()
            .loginByAwsIam(
                role, //the Vault role, not AWS IAM role
                // eg if configured with `vault write auth/aws/role/psoxy-gcal auth_type=iam policies={{YOUR_VAULT_POLICY}} max_ttl=500h bound_iam_principal_arn={{EXECUTION_ROLE_ARN}}`
                //  then --> `psoxy-gcal`
                Base64.getEncoder().encodeToString(prototypeRequest.getEndpoint().toString().getBytes(StandardCharsets.UTF_8)),
                iamRequestBody,
                Base64.getEncoder().encodeToString(serializedHeaders.getBytes(StandardCharsets.UTF_8)),
                "aws"
            );

        //above gives 403
        // ideas
        //   - something misconfigured in Vault?
        //   - AWS STS call fails?

        return authResponse.getAuthClientToken();
    }

    @VisibleForTesting
    void logCallerIdentity() {
        AWSSecurityTokenService awsSecurityTokenService = AWSSecurityTokenServiceClient.builder()
            .withRegion(region)
            .build();
        com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest request =
            new com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest();
        GetCallerIdentityResult r = awsSecurityTokenService.getCallerIdentity(request);

        //result is something like
        // arn:aws:sts::{{YOUR_AWS_ACCOUNT_ID}}:assumed-role/PsoxyExec_psoxy-gcal/psoxy-gcal
        log.info("getCallerIdentity: " + r.getArn());
    }

    /**
     * @return the prototype request that Vault should send to AWS to get the identity of the
     *   caller; if request succeeds, this proves validity of the signature and establishes that
     *   the caller has credentials for the identity returned by the request
     */
    @SneakyThrows
    @VisibleForTesting
    DefaultRequest buildGetCallerIdentityRequest(@NonNull String getVaultServerUrl) {

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(SERVER_ID_HEADER, getVaultServerUrl.split("//")[1]);
        //headers.put("User-Agent", "Psoxy/0.4.9");
        //ideas : try to migrate this all to v2? https://github.com/aws/aws-sdk-java-v2/blob/master/core/auth/src/main/java/software/amazon/awssdk/auth/signer/internal/BaseAws4Signer.java

        //try to differentiate Vault errors:
        // error is 403 Caused by: com.bettercloud.vault.VaultException: Vault responded with HTTP status code: 403
        //Response body: {"errors":["permission denied"]}
        // - but is that bc Vault's call to STS failed? or that it worked, but Vault didn't accept
        // the IAM principal?

        DefaultRequest getCallerIdentityRequest = new DefaultRequest("sts");
        getCallerIdentityRequest.setHttpMethod(HttpMethodName.POST);
        getCallerIdentityRequest.setEndpoint(new URI(getEndpoint()));
        getCallerIdentityRequest.setHeaders(headers);

        getCallerIdentityRequest.setContent(new ByteArrayInputStream(getPayload().getBytes(StandardCharsets.UTF_8)));

        AWS4Signer aws4Signer = new AWS4Signer();
        aws4Signer.setServiceName(getCallerIdentityRequest.getServiceName());
        aws4Signer.setRegionName(getRegion());

        //as for lambdas, this is a STS-issued credential which is temporary, AWS4Signer adds a
        // 'X-Amz-Security-Token' header; indeed this value is required to be included on requests
        // signed with STS credentials; so while this varies from Vault examples, it appears to be
        // the correct thing to do
        aws4Signer.sign(getCallerIdentityRequest, getCredentials());

        // from example https://docs.aws.amazon.com/STS/latest/APIReference/API_GetCallerIdentity.html,
        // these headers are included but not signed
        // but in example in Vault docs, headers are included AND signed
        // AWS docs seem to indicate that anything that's not X-AMZ is optional to be signed
        getCallerIdentityRequest.addHeader("Accept-Encoding", "identity");
        // vault example includes charset, but not in AWS docs don't
        getCallerIdentityRequest.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        //w/o Content-Length, trying this over curl gives 302; both AWS and vault examples include it
        getCallerIdentityRequest.addHeader("Content-Length", String.valueOf(getPayload().getBytes(StandardCharsets.UTF_8).length));

        return getCallerIdentityRequest;
    }

    @SneakyThrows
    @VisibleForTesting
    void preflightChecks(@NonNull String getVaultServerUrl) {
        //preflight check; if this doesn't work directly against AWS, then it won't work when
        // Vault sends to STS either
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse r = httpClient.execute(asHttpPost(buildGetCallerIdentityRequest(getVaultServerUrl)));
        if (r.getStatusLine().getStatusCode() == 200) {
            //if succeeds, content is the lambda's identity  (eg, it's IAM execution role)
            log.info("STS preflight check succeeded");
            //actual content is XML; don't want to mess around w XML parser so if you want to
            // double-chekc identity, uncomment the following:
            // log.info("Assumed rule: " + IOUtils.toString(r.getEntity().getContent(), StandardCharsets.UTF_8));
        } else {
            throw new RuntimeException("STS preflight failed: " + r.getStatusLine());
        }

        // TODO: this curl command times out (408) when run from local machine
        log.info(asCurlCommand(buildGetCallerIdentityRequest(getVaultServerUrl)).replace("\\\n", ""));
    }

    //for preflight check
    HttpPost asHttpPost(DefaultRequest awsRequest) {
        HttpPost httpPost = new HttpPost(awsRequest.getEndpoint());
        awsRequest.getHeaders().forEach((k, v) -> httpPost.addHeader((String) k, (String) v));

        //Apache HttpClient calculates Content-Length itself
        httpPost.removeHeaders("Content-Length");

        httpPost.setEntity(new StringEntity(getPayload(), StandardCharsets.UTF_8));
        return httpPost;
    }

    //for debugging
    @SneakyThrows
    String asCurlCommand(DefaultRequest awsRequest) {
        String asCurl = "curl -X POST -D \"" + IOUtils.toString(awsRequest.getContent(), StandardCharsets.UTF_8) + "\" \\\n";
        String headerString = (String) awsRequest.getHeaders().entrySet().stream()
            .map(entry ->  "-H \"" + ((Map.Entry<String, String>) entry).getKey() + ": " + ((Map.Entry<String, String>) entry).getValue() + "\"")
            .collect(Collectors.joining(" \\\n"));

        asCurl += headerString;
        asCurl += " " + awsRequest.getEndpoint().toString();
        return asCurl;
    }


    @VisibleForTesting
    String getEndpoint() {
        return "https://sts.amazonaws.com";
        //seen examples with this alternative
        //return String.format("https://sts.%s.amazonaws.com", getRegion());
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
