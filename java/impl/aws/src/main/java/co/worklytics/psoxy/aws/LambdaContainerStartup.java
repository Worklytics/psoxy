package co.worklytics.psoxy.aws;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import co.worklytics.psoxy.gateway.OutboundConnectivityFailures;
import co.worklytics.psoxy.gateway.impl.HealthCheckRequestHandler;
import lombok.extern.java.Log;

/**
 * Records a configuration-store connectivity failure during Lambda class initialization so the
 * invocation can still return a health-check response.
 *
 * <p>Static initialization reads SSM (the pseudonym salt). If that call cannot reach SSM, the
 * exception used to fail class init and API Gateway turned the invocation into an opaque 500/502.
 * Catching it here lets the handler respond with {@code CONFIG_STORE_UNREACHABLE}.
 *
 * <p>{@link #connectivityFailure} is written once during initialization and then only read.
 * The write happens before any request thread runs, except in tests.
 */
@Log
public final class LambdaContainerStartup {

    private static volatile Throwable connectivityFailure;

    private LambdaContainerStartup() {
    }

    public static void initialize(Runnable initializer) {
        try {
            initializer.run();
        } catch (Throwable e) {
            if (!OutboundConnectivityFailures.isConnectivityFailure(e)) {
                if (e instanceof Error error) {
                    throw error;
                }
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new ExceptionInInitializerError(e);
            }
            connectivityFailure = e;
            log.log(Level.SEVERE,
                    "Lambda initialization failed because the configuration store is unreachable. "
                            + "Health checks will report CONFIG_STORE_UNREACHABLE. "
                            + "Check VPC endpoints, NAT, and security groups. Underlying error: "
                            + OutboundConnectivityFailures.describe(e),
                    e);
        }
    }

    public static boolean failed() {
        return connectivityFailure != null;
    }

    public static HttpEventResponse response(String callerIp) {
        Throwable failure = connectivityFailure;
        if (failure == null) {
            failure = new IllegalStateException("configuration store unreachable");
        }
        return HealthCheckRequestHandler.configStoreUnreachable(callerIp, failure);
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
                    "Lambda failed to initialize because the configuration store is unreachable: "
                            + OutboundConnectivityFailures.describe(connectivityFailure));
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
    }
}
