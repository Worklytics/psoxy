package co.worklytics.psoxy.aws;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.annotations.VisibleForTesting;
import co.worklytics.psoxy.aws.request.APIGatewayV1ProxyEventRequestAdapter;
import co.worklytics.psoxy.aws.request.APIGatewayV2HTTPEventRequestAdapter;
import co.worklytics.psoxy.aws.request.LambdaEventUtils;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.HealthCheckRequestHandler;
import lombok.extern.java.Log;

/**
 * Records a configuration-store connectivity failure during Lambda class initialization so the
 * invocation can still return a health-check response.
 *
 * <p>Static initialization reads SSM (the pseudonym salt). If that call cannot reach SSM, the
 * exception used to fail class init and API Gateway turned the invocation into an opaque 500/502.
 * Catching it here lets the handler respond. A {@code ConfigStoreUnreachableException} is reported
 * as {@code CONFIG_STORE_UNREACHABLE}; any other connectivity failure is
 * {@code DEPENDENT_SERVICE_UNREACHABLE} and the client error is logged.
 *
 * <p>A connectivity failure is remembered so later invocations can respond immediately, then
 * retried after {@link #RETRY_AFTER}. A successful 503 does not recycle the execution environment,
 * so the next attempt after the cooldown is what recovers once the network path is fixed.
 * Only one thread runs that retry. The fields are written before request threads run, except in tests.
 */
@Log
public final class LambdaContainerStartup {

    static final Duration RETRY_AFTER = Duration.ofSeconds(60);

    private static volatile Throwable connectivityFailure;
    private static volatile Instant retryAfter = Instant.EPOCH;
    private static volatile Clock clock = Clock.systemUTC();

    private LambdaContainerStartup() {
    }

    public static void initialize(Runnable initializer) {
        try {
            initializer.run();
            connectivityFailure = null;
        } catch (Throwable e) {
            if (!AwsClientConnectivity.isConnectivityFailure(e)) {
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
            log.log(Level.SEVERE,
                    "Lambda initialization failed to connect to a dependent service. "
                            + "Underlying error: " + AwsClientConnectivity.describe(e),
                    e);
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
        return HealthCheckRequestHandler.unreachable(callerIp, failure);
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
                    "Lambda failed to initialize because a dependent service could not be reached: "
                            + AwsClientConnectivity.describe(connectivityFailure));
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
        return HealthCheckRequestHandler.callerIp(request);
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
