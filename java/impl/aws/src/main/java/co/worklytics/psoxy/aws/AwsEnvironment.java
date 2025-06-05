package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;

import java.util.Set;


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
