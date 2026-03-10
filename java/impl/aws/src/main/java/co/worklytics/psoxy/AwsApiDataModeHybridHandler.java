package co.worklytics.psoxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.time.Instant;
import java.util.logging.Level;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.aws.request.APIGatewayV1ProxyEventRequestAdapter;
import co.worklytics.psoxy.aws.request.APIGatewayV2HTTPEventRequestAdapter;
import co.worklytics.psoxy.aws.request.LambdaEventUtils;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import lombok.extern.java.Log;

/**
 * AWS lambda entrypoint that can handle API data requests via any of:
 * - cloud function URL invocations
 * - API Gateway events
 * - SQS events ( asynchronous processing of API data requests, that originally came from one of the
 * above)
 */
@Log
public class AwsApiDataModeHybridHandler implements RequestStreamHandler {

    /**
     * Static initialization allows reuse in containers
     * {@link "https://aws.amazon.com/premiumsupport/knowledge-center/lambda-improve-java-function-performance/"}
     */
    static AwsContainer awsContainer;

    // for parsing payloads that our code-serialized to JSON (Eg, SQS message content)
    static ObjectMapper payloadMapper;
    static ApiDataRequestHandler requestHandler;
    static ResponseCompressionHandler responseCompressionHandler;
    static LambdaEventUtils lambdaEventUtils;

    static {
        staticInit();
    }

    private static void staticInit() {
        awsContainer = DaggerAwsContainer.create();
        payloadMapper = awsContainer.objectMapper();
        requestHandler = awsContainer.apiDataRequestHandler();
        responseCompressionHandler = new ResponseCompressionHandler();
        lambdaEventUtils = awsContainer.lambdaEventUtils();

        if (awsContainer.loggingConfiguration().isNewRelicEnabled()) {
            io.opentracing.util.GlobalTracer.registerIfAbsent(com.newrelic.opentracing.LambdaTracer.INSTANCE);
        }

        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context)
            throws IOException {
        // Read the full input stream into a tree
        JsonNode rootNode = lambdaEventUtils.read(input);

        if (lambdaEventUtils.isSQSEvent(rootNode)) {
            // async invocation case via SQS
            SQSEvent sqsEvent = lambdaEventUtils.toSQSEvent(rootNode);
            if (awsContainer.loggingConfiguration().isNewRelicEnabled()) {
                com.newrelic.opentracing.aws.LambdaTracing.instrument(sqsEvent, context, (inEvent, ctx) -> {
                    handleSqsEvent(inEvent, ctx);
                    return null;
                });
            } else {
                handleSqsEvent(sqsEvent, context);
            }
        } else {
            // synchronous invocation case - API Gateway or cloud function URL invocation
            ApiDataRequestHandler.ProcessingContext processingContext = ApiDataRequestHandler.ProcessingContext.builder()
                    .requestId(context.getAwsRequestId())
                    .requestReceivedAt(Instant.now())
                    .build();

            Pair<Boolean, HttpEventResponse> responsePair;
            if (lambdaEventUtils.isApiGatewayV1Event(rootNode)) {
                APIGatewayProxyRequestEvent v1Event = lambdaEventUtils.toAPIGatewayProxyRequestEvent(rootNode);
                if (awsContainer.loggingConfiguration().isNewRelicEnabled()) {
                    responsePair = com.newrelic.opentracing.aws.LambdaTracing.instrument(v1Event, context, (inEvt, ctx) -> {
                        try { return handleSingleRequest(rootNode, processingContext, ctx); } 
                        catch (IOException e) { throw new RuntimeException(e); }
                    });
                } else {
                    responsePair = handleSingleRequest(rootNode, processingContext, context);
                }
                lambdaEventUtils.writeAsApiGatewayV1Response(responsePair.getRight(), output, responsePair.getLeft());
            } else if (lambdaEventUtils.isApiGatewayV2Event(rootNode)) {
                APIGatewayV2HTTPEvent v2Event = lambdaEventUtils.toAPIGatewayV2HTTPEvent(rootNode);
                if (awsContainer.loggingConfiguration().isNewRelicEnabled()) {
                    responsePair = com.newrelic.opentracing.aws.LambdaTracing.instrument(v2Event, context, (inEvt, ctx) -> {
                        try { return handleSingleRequest(rootNode, processingContext, ctx); } 
                        catch (IOException e) { throw new RuntimeException(e); }
                    });
                } else {
                    responsePair = handleSingleRequest(rootNode, processingContext, context);
                }
                lambdaEventUtils.writeAsApiGatewayV2Response(responsePair.getRight(), output, responsePair.getLeft());
            } else {
                throw new IllegalArgumentException(
                        "Unsupported event type: " + rootNode.getNodeType());
            }
        }
    }

    private void handleSqsEvent(SQSEvent sqsEvent, Context context) {
        int messageCount = sqsEvent.getRecords().size();
        int failedMessageCount = 0;
        for (SQSMessage message : sqsEvent.getRecords()) {
            try {
                if (!message.getMessageAttributes().containsKey("processingContext")) {
                    throw new IllegalArgumentException(
                            "SQS event record has no processingContext message attribute");
                }

                String processingContextJson = message.getMessageAttributes()
                        .get("processingContext").getStringValue();

                ApiDataRequestHandler.ProcessingContext processingContext = payloadMapper.readValue(processingContextJson,
                        ApiDataRequestHandler.ProcessingContext.class);
                JsonNode rootNode = payloadMapper.readTree(message.getBody());

                handleSingleRequest(rootNode, processingContext, context);

                // async case - SQS; no response is needed
                log.info("Processed async API data request: "
                        + payloadMapper.writeValueAsString(processingContext));
            } catch (Throwable e) {
                log.log(Level.WARNING,
                        "Error processing async API data request: " + e.getMessage(), e);
                // TODO: send JUST this message to the dead letter queue ??
                failedMessageCount++;
            }
        }
        log.info("Processed " + messageCount + " async API data requests; " + failedMessageCount
                + " failed");
        if (failedMessageCount > 0) {
            throw new RuntimeException(
                    "Failed to process " + failedMessageCount + " async API data requests");
        }
    }

    private Pair<Boolean, HttpEventResponse> handleSingleRequest(JsonNode rootNode,
            ApiDataRequestHandler.ProcessingContext processingContext, Context context)
            throws IOException {
        HttpEventRequest request;
        if (lambdaEventUtils.isApiGatewayV1Event(rootNode)) {
            APIGatewayProxyRequestEvent v1Event =
                    lambdaEventUtils.toAPIGatewayProxyRequestEvent(rootNode);
            if (!processingContext.getAsync()) {
                // for consistency, take the requestId and requestReceivedAt from the event
                processingContext = processingContext.toBuilder()
                        .requestId(v1Event.getRequestContext().getRequestId())
                        .requestReceivedAt(Instant
                                .ofEpochMilli(v1Event.getRequestContext().getRequestTimeEpoch()))
                        .build();
            }
            request = APIGatewayV1ProxyEventRequestAdapter.of(v1Event);
        } else if (lambdaEventUtils.isApiGatewayV2Event(rootNode)) {
            APIGatewayV2HTTPEvent v2Event = lambdaEventUtils.toAPIGatewayV2HTTPEvent(rootNode);
            if (!processingContext.getAsync()) {
                // for consistency, take the requestId and requestReceivedAt from the event
                processingContext = processingContext.toBuilder()
                        .requestId(v2Event.getRequestContext().getRequestId())
                        .requestReceivedAt(
                                Instant.ofEpochMilli(v2Event.getRequestContext().getTimeEpoch()))
                        .build();
            }
            request = new APIGatewayV2HTTPEventRequestAdapter(v2Event);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported event: " + payloadMapper.writeValueAsString(rootNode));
        }

        HttpEventResponse response;
        try {
            response = requestHandler.handle(request, processingContext);

            if (responseCompressionHandler.isCompressionRequested(request)) {
                return responseCompressionHandler.compressIfNeeded(response);
            } else {
                return Pair.of(false, response);
            }
        } catch (Throwable e) {

            context.getLogger()
                    .log(String.format("%s - %s", e.getClass().getName(), e.getMessage()));
            context.getLogger().log(ExceptionUtils.getStackTrace(e));
            return Pair.of(false, HttpEventResponse.builder()
                    .statusCode(500)
                    .body("Unknown error: " + e.getClass().getName())
                    .header(ProcessedDataMetadataFields.ERROR.getHttpHeader(), "Unknown error")
                    .build());
        }
    }
}
