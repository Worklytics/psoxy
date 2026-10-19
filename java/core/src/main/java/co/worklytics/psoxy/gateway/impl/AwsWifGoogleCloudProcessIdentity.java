package co.worklytics.psoxy.gateway.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.AwsCredentialSource;
import com.google.auth.oauth2.AwsCredentials;
import com.google.auth.oauth2.ExternalAccountCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Utils;
import co.worklytics.psoxy.gateway.ConfigService;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Process identity for AWS-hosted workloads: AWS IAM credentials (Lambda execution role, etc)
 * federated to a GCP principal via Workload Identity Federation.
 *
 * <p>That GCP principal then calls IAM {@code signJwt} as the domain-wide-delegation service
 * account. Typical binding: grant the WIF principal (or the SA named in
 * {@code GCP_WIF_SERVICE_ACCOUNT_IMPERSONATION_URL}) {@code roles/iam.serviceAccountTokenCreator}
 * on the DWD SA.
 *
 * <p>Does not use Application Default Credentials or {@code GOOGLE_APPLICATION_CREDENTIALS}.
 * AWS credentials are the Lambda/task execution role, via the env vars AWS injects
 * ({@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY}, {@code AWS_SESSION_TOKEN},
 * {@code AWS_REGION}) — not EC2 instance metadata.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class AwsWifGoogleCloudProcessIdentity implements GoogleCloudProcessIdentity {

    public static final String CONFIG_IDENTIFIER = "aws_wif";

    /**
     * STS GetCallerIdentity URL template; google-auth-library substitutes {@code {region}}.
     */
    static final String DEFAULT_REGIONAL_CRED_VERIFICATION_URL =
        "https://sts.{region}.amazonaws.com?Action=GetCallerIdentity&Version=2011-06-15";

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        /**
         * WIF audience, e.g.
         * {@code //iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/POOL_ID/providers/PROVIDER_ID}
         */
        GCP_WIF_AUDIENCE,
        /**
         * GCP STS token URL; defaults to {@code https://sts.googleapis.com/v1/token}
         */
        GCP_WIF_TOKEN_URL,
        /**
         * Optional. If set, WIF STS token is exchanged for a GCP SA access token via IAM
         * {@code generateAccessToken} before {@code signJwt}. Use when the WIF principal is granted
         * workloadIdentityUser on a runtime SA, and that SA (not the WIF principal) has Token
         * Creator on the DWD SA.
         */
        GCP_WIF_SERVICE_ACCOUNT_IMPERSONATION_URL,
        ;

        @Getter(onMethod_ = @Override)
        private final SupportedSource supportedSource = SupportedSource.ENV_VAR;
    }

    @Getter
    private final String configIdentifier = CONFIG_IDENTIFIER;

    @Inject
    ConfigService config;
    @Inject
    HttpTransportFactory httpTransportFactory;

    @Override
    public GoogleCredentials getCredentials() {
        AwsCredentials.Builder builder = AwsCredentials.newBuilder()
            .setHttpTransportFactory(httpTransportFactory)
            .setAudience(config.getConfigPropertyOrError(ConfigProperty.GCP_WIF_AUDIENCE))
            .setSubjectTokenType(ExternalAccountCredentials.SubjectTokenTypes.AWS4)
            .setTokenUrl(config.getConfigPropertyAsOptional(ConfigProperty.GCP_WIF_TOKEN_URL)
                .filter(StringUtils::isNotBlank)
                .orElse(defaultStsTokenUrl()))
            .setCredentialSource(new AwsCredentialSource(awsCredentialSourceFromExecutionRole()))
            .setScopes(List.of(OAuth2Utils.CLOUD_PLATFORM_SCOPE));

        config.getConfigPropertyAsOptional(ConfigProperty.GCP_WIF_SERVICE_ACCOUNT_IMPERSONATION_URL)
            .filter(StringUtils::isNotBlank)
            .ifPresent(builder::setServiceAccountImpersonationUrl);

        return builder.build();
    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(ConfigProperty.GCP_WIF_AUDIENCE);
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ConfigProperty.values());
    }

    static String defaultStsTokenUrl() {
        return String.format(OAuth2Utils.TOKEN_EXCHANGE_URL_FORMAT, "googleapis.com");
    }

    /**
     * Tells google-auth-library to federate using the AWS credentials already on this process
     * (Lambda execution role env vars). {@code regional_cred_verification_url} is required by
     * {@link AwsCredentialSource}; IMDS URLs are omitted because we are not reaching out to
     * 169.254.169.254 — Lambda injects the role session as environment variables.
     */
    static Map<String, Object> awsCredentialSourceFromExecutionRole() {
        Map<String, Object> source = new HashMap<>();
        source.put("environment_id", "aws1");
        source.put("regional_cred_verification_url", DEFAULT_REGIONAL_CRED_VERIFICATION_URL);
        return source;
    }
}
