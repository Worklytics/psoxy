package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;

import java.util.Optional;
import java.util.Set;

import lombok.Builder;
import lombok.Value;


public class AwsEnvironment implements HostEnvironment {

    /**
     * see "https://docs.aws.amazon.com/lambda/latest/dg/configuration-envvars.html#configuration-envvars-runtimer"
     */
    private enum RuntimeEnvironmentVariables {
        AWS_REGION,
        AWS_LAMBDA_FUNCTION_NAME,
    }

    @Override
    public String getInstanceId() {
        return System.getenv(RuntimeEnvironmentVariables.AWS_LAMBDA_FUNCTION_NAME.name());
    }

    public String getRegion() {
        return System.getenv(RuntimeEnvironmentVariables.AWS_REGION.name());
    }

    @Builder
    @Value
    static class AwsApiModeConfig {

        /**
         * SQS queue URL to which async API data requests should be sent, if any
         */
        Optional<String> asyncApiRequestQueueUrl;

        enum AwsApiModeConfigProperty implements ConfigService.ConfigProperty {

            ASYNC_API_REQUEST_QUEUE_URL,
        }

        static AwsApiModeConfig fromConfigService(ConfigService configService) {
            return AwsApiModeConfig.builder()
                .asyncApiRequestQueueUrl(configService.getConfigPropertyAsOptional(AwsApiModeConfigProperty.ASYNC_API_REQUEST_QUEUE_URL))
                .build();
        }
    }

    enum AwsConfigProperty implements ConfigService.ConfigProperty {
        SECRETS_STORE,
        ;
    }


    enum SecretStoreImplementations {
        AWS_SSM_PARAMETER_STORE,
        AWS_SECRETS_MANAGER,
        HASHICORP_VAULT,
        ;
    }

    @Override
    public Set<String> getSupportedOutputKinds() {
        return Set.of("s3", "sqs");
    }
}
