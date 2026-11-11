package co.worklytics.psoxy.gateway;

import java.util.Optional;

/**
 * Recognizes a client-side failure to reach a service the proxy depends on.
 *
 * <p>Platform modules contribute their own implementation for SDK-specific errors. Core contributes
 * the shared {@code java.net} cases. Implementations are stateless and safe to call from any thread.
 */
public interface ConnectivityFailures {

    /**
     * @return the failure when {@code throwable} or one of its causes is a connectivity problem.
     * The service token, when present, is a short parsed name such as {@code ssm} or {@code kms}.
     * It is never the raw exception message.
     */
    Optional<DependencyConnectivityFailure> match(Throwable throwable);
}
