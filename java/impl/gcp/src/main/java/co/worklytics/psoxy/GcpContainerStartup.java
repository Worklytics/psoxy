package co.worklytics.psoxy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.logging.Level;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.java.Log;

/**
 * Starts the GCP Dagger container once per function instance.
 *
 * <p>A transport failure is remembered so later invocations can respond immediately, then retried
 * after {@link #RETRY_AFTER}. Cloud Functions does not recycle an instance that keeps serving
 * responses, so the cooldown retry is what recovers once the network path is fixed. Only one
 * thread runs the factory.
 */
@Log
public final class GcpContainerStartup {

    static final Duration RETRY_AFTER = Duration.ofSeconds(60);

    private volatile GcpContainer container;
    private volatile Throwable connectivityFailure;
    private volatile Instant retryAfter = Instant.EPOCH;
    private volatile Clock clock = Clock.systemUTC();

    public GcpContainer getOrCreate(Supplier<GcpContainer> factory) {
        if (container != null) {
            return container;
        }
        if (connectivityFailure != null && clock.instant().isBefore(retryAfter)) {
            return null;
        }
        synchronized (this) {
            if (container != null) {
                return container;
            }
            if (connectivityFailure != null && clock.instant().isBefore(retryAfter)) {
                return null;
            }
            try {
                container = factory.get();
                connectivityFailure = null;
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
                retryAfter = clock.instant().plus(RETRY_AFTER);
                log.log(Level.SEVERE,
                        "Cloud Function initialization failed to connect to a dependent service. "
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
        retryAfter = Instant.EPOCH;
        clock = Clock.systemUTC();
    }

    @VisibleForTesting
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
