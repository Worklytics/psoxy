package co.worklytics.psoxy;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.common.annotations.VisibleForTesting;
import co.worklytics.psoxy.gateway.ConnectivityFailureMatcher;
import co.worklytics.psoxy.gateway.ConnectivityFailureResponses;
import co.worklytics.psoxy.gateway.DependencyConnectivityFailure;
import co.worklytics.psoxy.gateway.DependencyServiceNames;
import co.worklytics.psoxy.gateway.JavaNetConnectivityFailures;
import lombok.extern.java.Log;

/**
 * Starts the GCP Dagger container once per function instance.
 *
 * <p>A transport failure is remembered so later invocations can respond immediately, then retried
 * after {@link #RETRY_AFTER}. Cloud Functions does not recycle an instance that keeps serving
 * responses, so the cooldown retry is what recovers once the network path is fixed. Only one
 * thread runs the factory. The parsers are constructed with {@code new} because the Dagger graph
 * is what failed to start. After a successful start, request handling uses the matcher from
 * {@link GcpContainer}.
 */
@Log
public final class GcpContainerStartup {

    static final Duration RETRY_AFTER = Duration.ofSeconds(60);

    private final ConnectivityFailureMatcher connectivityFailures = startupMatcher();

    /**
     * Same mapper settings as {@code PsoxyModule}, constructed here because this runs when the
     * Dagger graph did not start. Request handling uses the injected
     * {@link ConnectivityFailureResponses}.
     */
    private final ConnectivityFailureResponses responses = startupResponses();

    private volatile GcpContainer container;
    private volatile Throwable connectivityFailure;
    private volatile Instant retryAfter = Instant.EPOCH;
    private volatile Clock clock = Clock.systemUTC();

    private static ConnectivityFailureMatcher startupMatcher() {
        DependencyServiceNames names = new DependencyServiceNames();
        return new ConnectivityFailureMatcher(Set.of(
                new JavaNetConnectivityFailures(names),
                new GcpConnectivityFailures(names)));
    }

    private static ConnectivityFailureResponses startupResponses() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return new ConnectivityFailureResponses(objectMapper);
    }

    public void write(HttpRequest request, HttpResponse response) throws IOException {
        Throwable cause = connectivityFailure;
        if (cause == null) {
            cause = new IllegalStateException("dependent service unreachable");
        }
        DependencyConnectivityFailure failure = connectivityFailures.match(cause)
                .orElseGet(() -> DependencyConnectivityFailure.builder().build());
        GcpConfigStoreResponses.write(request, response, responses, failure, cause, true);
    }

    public Optional<DependencyConnectivityFailure> match(Throwable throwable) {
        return connectivityFailures.match(throwable);
    }

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
                if (connectivityFailures.match(e).isEmpty()) {
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
                        "Cloud Function initialization failed to connect to a dependent service", e);
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
