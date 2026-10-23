package co.worklytics.psoxy.aws;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Level;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.annotations.VisibleForTesting;
import co.worklytics.psoxy.aws.request.APIGatewayV1ProxyEventRequestAdapter;
import co.worklytics.psoxy.aws.request.APIGatewayV2HTTPEventRequestAdapter;
import co.worklytics.psoxy.aws.request.LambdaEventUtils;
import co.worklytics.psoxy.gateway.ConnectivityFailureMatcher;
import co.worklytics.psoxy.gateway.ConnectivityFailureResponses;
import co.worklytics.psoxy.gateway.DependencyConnectivityFailure;
import co.worklytics.psoxy.gateway.DependencyServiceNames;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.JavaNetConnectivityFailures;
import lombok.extern.java.Log;

/**
 * Records a configuration-store connectivity failure during Lambda class initialization so the
 * invocation can still return a health-check response.
 *
 * <p>Static initialization reads SSM (the pseudonym salt). If that call cannot reach SSM, the
 * exception used to fail class init and API Gateway turned the invocation into an opaque 500/502.
 * Catching it here lets the handler respond. The response is a 503. The client error is logged;
 * the body names a parsed service such as {@code ssm} when one is known. This class constructs
 * the parsers with {@code new} because the Dagger graph is what failed to start.
 *
 * <p>A connectivity failure is remembered so later invocations can respond immediately, then
 * retried after {@link #RETRY_AFTER}. A successful 503 does not recycle the execution environment,
 * so the next attempt after the cooldown is what recovers once the network path is fixed.
 * Only one thread runs that retry. The fields are written before request threads run, except in tests.
 */
@Log
public final class LambdaContainerStartup {

    static final Duration RETRY_AFTER = Duration.ofSeconds(60);

    /**
     * Built before Dagger exists. Request handling after a successful start uses the matcher
     * from {@link AwsContainer}.
     */
    private static final ConnectivityFailureMatcher CONNECTIVITY = startupMatcher();

    /**
     * Same mapper settings as {@code PsoxyModule}, constructed here because this runs when the
     * Dagger graph did not start. Request handling uses the injected
     * {@link ConnectivityFailureResponses}.
     */
    private static final ConnectivityFailureResponses RESPONSES = startupResponses();

    private static volatile Throwable connectivityFailure;
    private static volatile Instant retryAfter = Instant.EPOCH;
    private static volatile Clock clock = Clock.systemUTC();

    private LambdaContainerStartup() {
    }

    private static ConnectivityFailureMatcher startupMatcher() {
        DependencyServiceNames names = new DependencyServiceNames();
        return new ConnectivityFailureMatcher(Set.of(
                new JavaNetConnectivityFailures(names),
                new AwsConnectivityFailures(names)));
    }

    private static ConnectivityFailureResponses startupResponses() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return new ConnectivityFailureResponses(objectMapper);
    }

    public static void initialize(Runnable initializer) {
        try {
            initializer.run();
            connectivityFailure = null;
        } catch (Throwable e) {
            if (CONNECTIVITY.match(e).isEmpty()) {
                if (e instanceof Error error) {
                    throw error;
                }
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new ExceptionInInitializerError(e);
            }
            connectivityFailure = e;
            retryAfter = clock.instant().plus(RETRY_AFTER);
            log.log(Level.SEVERE, "Lambda initialization failed to connect to a dependent service", e);
        }
    }

    public static boolean failed() {
        return connectivityFailure != null;
    }

    /**
     * @return true when the caller should return the cached startup failure now. After
     * {@link #RETRY_AFTER}, {@code retry} runs once; a success clears the failure.
     */
    public static boolean stillFailed(Runnable retry) {
        if (connectivityFailure == null) {
            return false;
        }
        if (clock.instant().isBefore(retryAfter)) {
            return true;
        }
        synchronized (LambdaContainerStartup.class) {
            if (connectivityFailure == null) {
                return false;
            }
            if (clock.instant().isBefore(retryAfter)) {
                return true;
            }
            initialize(retry);
            return connectivityFailure != null;
        }
    }

    public static HttpEventResponse response(String callerIp) {
        Throwable failure = connectivityFailure;
        if (failure == null) {
            failure = new IllegalStateException("dependent service unreachable");
        }
        DependencyConnectivityFailure parsed = CONNECTIVITY.match(failure)
                .orElseGet(() -> DependencyConnectivityFailure.builder().build());
        return RESPONSES.toResponse(callerIp, parsed, failure, true);
    }

    /**
     * @return true when a startup failure response was written and the caller should not continue
     */
    public static boolean writeIfStartupFailed(InputStream input, OutputStream output) throws IOException {
        if (connectivityFailure == null) {
            return false;
        }
        ObjectMapper mapper = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .build();
        JsonNode root = mapper.readTree(input);
        LambdaEventUtils utils = new LambdaEventUtils(mapper);
        if (utils.isSQSEvent(root)) {
            throw new IllegalStateException(
                    "Lambda failed to initialize because " + startupDetail(connectivityFailure),
                    connectivityFailure);
        }
        if (utils.isApiGatewayV1Event(root)) {
            APIGatewayProxyRequestEvent event = utils.toAPIGatewayProxyRequestEvent(root);
            utils.writeAsApiGatewayV1Response(response(callerIp(APIGatewayV1ProxyEventRequestAdapter.of(event))),
                    output, false);
            return true;
        }
        String callerIp = "unknown";
        if (utils.isApiGatewayV2Event(root)) {
            APIGatewayV2HTTPEvent event = utils.toAPIGatewayV2HTTPEvent(root);
            callerIp = callerIp(new APIGatewayV2HTTPEventRequestAdapter(event));
        }
        utils.writeAsApiGatewayV2Response(response(callerIp), output, false);
        return true;
    }

    public static String callerIp(HttpEventRequest request) {
        return RESPONSES.callerIp(request);
    }

    private static String startupDetail(Throwable failure) {
        DependencyConnectivityFailure parsed = CONNECTIVITY.match(failure)
                .orElseGet(() -> DependencyConnectivityFailure.builder().build());
        if (parsed.getClientMessage() != null) {
            return parsed.getClientMessage();
        }
        if (parsed.getService() != null) {
            return parsed.getService() + " could not be reached. Check the logs.";
        }
        return "a dependent service could not be reached. Check the logs.";
    }

    @VisibleForTesting
    static void reset() {
        connectivityFailure = null;
        retryAfter = Instant.EPOCH;
        clock = Clock.systemUTC();
    }

    @VisibleForTesting
    static void setClock(Clock clock) {
        LambdaContainerStartup.clock = clock;
    }
}
