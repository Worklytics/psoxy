package co.worklytics.psoxy.gateway;

/**
 * The proxy could not reach its configuration store (for example AWS SSM Parameter Store or
 * Secrets Manager) because the client could not complete the HTTP call.
 *
 * <p>Distinct from {@link TransientConfigException}, which is a retryable service or credential
 * blip. This failure stays until network path to the store is fixed (VPC endpoint, NAT, security
 * group).
 */
public class ConfigStoreUnreachableException extends RuntimeException {

    public ConfigStoreUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
