package co.worklytics.psoxy.aws;

import java.util.Optional;
import javax.inject.Inject;
import co.worklytics.psoxy.gateway.ConnectivityFailures;
import co.worklytics.psoxy.gateway.DependencyConnectivityFailure;
import co.worklytics.psoxy.gateway.DependencyServiceNames;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * AWS SDK client failures that mean the call never completed an HTTP request.
 *
 * <p>Credential-chain failures are also {@link SdkClientException} and are not a connectivity
 * problem. Stateless and safe to call from any thread. {@code java.net} failures are recognized
 * by {@link co.worklytics.psoxy.gateway.JavaNetConnectivityFailures}, which is bound beside this one.
 */
@AllArgsConstructor(onConstructor_ = @Inject)
public class AwsConnectivityFailures implements ConnectivityFailures {

    static final String HTTP_REQUEST_FAILURE = "Unable to execute HTTP request";

    private final DependencyServiceNames serviceNames;

    @Override
    public Optional<DependencyConnectivityFailure> match(Throwable throwable) {
        String service = null;
        boolean httpFailure = false;
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SdkClientException) {
                String message = current.getMessage();
                if (message != null && message.contains(HTTP_REQUEST_FAILURE)) {
                    httpFailure = true;
                    if (service == null) {
                        service = serviceNames.findIn(message).orElse(null);
                    }
                }
            }
            current = current.getCause();
        }
        if (!httpFailure) {
            return Optional.empty();
        }
        if (service == null) {
            service = serviceInChain(throwable);
        }
        return Optional.of(DependencyConnectivityFailure.builder()
                .service(service)
                .clientMessage(clientMessage(service))
                .build());
    }

    private String serviceInChain(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            Optional<String> service = serviceNames.findIn(current.getMessage());
            if (service.isPresent()) {
                return service.get();
            }
            current = current.getCause();
        }
        return null;
    }

    private String clientMessage(String service) {
        if ("ssm".equals(service)) {
            return "Unable to reach SSM Parameter Store. Check the logs.";
        }
        if ("secretsmanager".equals(service)) {
            return "Unable to reach Secrets Manager. Check the logs.";
        }
        if ("kms".equals(service)) {
            return "Unable to reach KMS. Check the logs.";
        }
        if ("s3".equals(service)) {
            return "Unable to reach S3. Check the logs.";
        }
        if ("logs".equals(service)) {
            return "Unable to reach CloudWatch Logs. Check the logs.";
        }
        if ("sts".equals(service)) {
            return "Unable to reach STS. Check the logs.";
        }
        if ("jwks".equals(service)) {
            return "Unable to reach a JWKS endpoint. Check the logs.";
        }
        return "Unable to reach an AWS API. Check the logs.";
    }
}
