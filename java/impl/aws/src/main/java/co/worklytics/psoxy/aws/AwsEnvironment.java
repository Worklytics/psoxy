package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.HostEnvironment;


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


}
