package co.worklytics.psoxy.aws;

import org.apache.commons.lang3.StringUtils;
import co.worklytics.psoxy.gateway.NetworkConnectivityFailures;
import co.worklytics.psoxy.utils.LogSanitizationUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * AWS SDK client failures that mean the Lambda could not complete an HTTP call to an AWS API
 * (SSM, Secrets Manager, and the same client error from other AWS SDK calls).
 *
 * <p>Credential-chain failures are also {@link SdkClientException} and are not treated as a
 * connectivity problem.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AwsClientConnectivity {

    static final String HTTP_REQUEST_FAILURE = "Unable to execute HTTP request";

    public static boolean isConnectivityFailure(Throwable throwable) {
        return isSdkHttpFailure(throwable) || NetworkConnectivityFailures.isConnectivityFailure(throwable);
    }

    public static boolean isSdkHttpFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SdkClientException) {
                String message = current.getMessage();
                if (message != null && message.contains(HTTP_REQUEST_FAILURE)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Prefers the AWS SDK HTTP failure text, which is what CloudWatch logs for SSM.
     */
    public static String describe(Throwable throwable) {
        String sdkMessage = sdkHttpMessage(throwable);
        if (sdkMessage != null) {
            return StringUtils.abbreviate(LogSanitizationUtils.redactPotentialPii(sdkMessage), 400);
        }
        return NetworkConnectivityFailures.describe(throwable);
    }

    private static String sdkHttpMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = StringUtils.trimToNull(current.getMessage());
            if (message != null && message.contains(HTTP_REQUEST_FAILURE)) {
                return message;
            }
            current = current.getCause();
        }
        return null;
    }
}
