package co.worklytics.psoxy;

import java.util.function.Supplier;
import java.util.logging.Level;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.java.Log;

/**
 * Starts the GCP Dagger container once per function instance.
 *
 * <p>A Secret Manager transport failure is remembered so later invocations can return
 * {@code CONFIG_STORE_UNREACHABLE} without waiting out another client deadline. The factory runs
 * on at most one thread; after a failure it is not retried on this instance. Cloud Functions
 * recycles the instance after the network path is fixed.
 */
@Log
public final class GcpContainerStartup {

    private volatile GcpContainer container;
    private volatile Throwable connectivityFailure;

    public GcpContainer getOrCreate(Supplier<GcpContainer> factory) {
        if (connectivityFailure != null) {
            return null;
        }
        if (container != null) {
            return container;
        }
        synchronized (this) {
            if (connectivityFailure != null) {
                return null;
            }
            if (container != null) {
                return container;
            }
            try {
                container = factory.get();
                return container;
            } catch (Throwable e) {
                if (!GcpClientConnectivity.isTransportFailure(e)) {
                    if (e instanceof Error error) {
                        throw error;
                    }
                    if (e instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IllegalStateException(e);
                }
                connectivityFailure = e;
                log.log(Level.SEVERE,
                        "Cloud Function initialization failed because Secret Manager is unreachable. "
                                + "Health checks will report CONFIG_STORE_UNREACHABLE. "
                                + "With VPC egress set to all traffic, enable Private Google Access or Cloud NAT. "
                                + "Underlying error: " + GcpClientConnectivity.describe(e),
                        e);
                return null;
            }
        }
    }

    public boolean failed() {
        return connectivityFailure != null;
    }

    public Throwable failure() {
        return connectivityFailure;
    }

    @VisibleForTesting
    void reset() {
        container = null;
        connectivityFailure = null;
    }
}
