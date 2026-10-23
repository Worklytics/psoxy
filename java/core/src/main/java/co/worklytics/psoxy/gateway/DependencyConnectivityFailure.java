package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Value;

/**
 * A connectivity failure safe to put in a client response.
 *
 * <p>{@link #service} is a parsed token ({@code ssm}, {@code secretsmanager}, {@code secretmanager},
 * {@code kms}, {@code jwks}, and similar). It is null when the failure is a connection problem but
 * the dependency could not be identified from a known host.
 *
 * <p>{@link #clientMessage} is a short sentence from the platform parser, such as
 * {@code Unable to reach SSM Parameter Store. Check the logs.} It is never the raw exception
 * message. Core leaves it null and the response writer uses a one-line fallback.
 */
@Value
@Builder
public class DependencyConnectivityFailure {

    String service;

    String clientMessage;

    public boolean isConfigStore() {
        return "ssm".equals(service) || "secretsmanager".equals(service) || "secretmanager".equals(service);
    }
}
