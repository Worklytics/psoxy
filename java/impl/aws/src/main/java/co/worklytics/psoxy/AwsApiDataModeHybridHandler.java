package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.aws.request.APIGatewayV1ProxyEventRequestAdapter;
import co.worklytics.psoxy.aws.request.APIGatewayV2HTTPEventRequestAdapter;
import co.worklytics.psoxy.aws.request.LambdaEventUtils;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.java.Log;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * AWS lambda entrypoint that can handle API data requests via any of:
 *   - cloud function URL invocations
 *   - API Gateway events
 *   - SQS events ( asynchronous processing of API data requests, that originally came from one of the above)
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
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        // Read the full input stream into a tree
        JsonNode rootNode = lambdaEventUtils.read(input);

        ApiDataRequestHandler.ProcessingContext processingContext;

        // whatever the event is, convert to HttpEventRequest and invoke the request handler
        HttpEventResponse response;
        if (lambdaEventUtils.isSQSEvent(rootNode)) {

            JsonNode records = rootNode.get("Records");
            if (records == null || records.isEmpty()) {
                throw new IllegalArgumentException("SQS event has no records");
            }

            for (JsonNode record : records) {
                if (!record.has("body")) {
                    throw new IllegalArgumentException("SQS event record has no body");
                }
                if (!record.has("messageAttributes") || !record.get("messageAttributes").has("processingContext")) {
                    throw new IllegalArgumentException("SQS event record has no processingContext message attribute");
                }
                rootNode = payloadMapper.readTree(record.get("body").asText());

                processingContext = payloadMapper.readValue(records.get("messageAttributes").get("processingContext").get("stringValue").asText(),
                    ApiDataRequestHandler.ProcessingContext.class);

                handleSingleRequest(rootNode, processingContext, context);

                // async case - SQS; no response is needed
                log.info("Processed async API data request: " + payloadMapper.writeValueAsString(processingContext));
            }
        } else {
            processingContext = ApiDataRequestHandler.ProcessingContext.builder().build();
            Pair<Boolean, HttpEventResponse> responsePair = handleSingleRequest(rootNode, processingContext, context);
            response = responsePair.getRight();
            Boolean base64Encoded = responsePair.getLeft();

            // need to send the proper response
            if (lambdaEventUtils.isApiGatewayV1Event(rootNode)) {
                lambdaEventUtils.writeAsApiGatewayV1Response(response, output, base64Encoded);
            } else if (lambdaEventUtils.isApiGatewayV2Event(rootNode)) {
                lambdaEventUtils.writeAsApiGatewayV2Response(response, output, base64Encoded);
            } else {
                throw new IllegalArgumentException("Unsupported event type: " + rootNode.getNodeType());
            }
        }
    }

    private Pair<Boolean, HttpEventResponse> handleSingleRequest(JsonNode rootNode, ApiDataRequestHandler.ProcessingContext processingContext, Context context) throws IOException {
        HttpEventRequest request;
        if (lambdaEventUtils.isApiGatewayV1Event(rootNode)) {
            request = APIGatewayV1ProxyEventRequestAdapter.of(lambdaEventUtils.toAPIGatewayProxyRequestEvent(rootNode));
        } else if (lambdaEventUtils.isApiGatewayV2Event(rootNode)) {
            request = new APIGatewayV2HTTPEventRequestAdapter(lambdaEventUtils.toAPIGatewayV2HTTPEvent(rootNode));
        } else {
            throw new IllegalArgumentException("Unsupported event type: " + rootNode.getNodeType());
        }

        HttpEventResponse response;
        try {
            response = requestHandler.handle(request, processingContext);

            if (responseCompressionHandler.isCompressionRequested(request)) {
                return  responseCompressionHandler.compressIfNeeded(response);
            } else{
                return Pair.of(false, response);
            }
        } catch (Throwable e) {

            context.getLogger().log(String.format("%s - %s", e.getClass().getName(), e.getMessage()));
            context.getLogger().log(ExceptionUtils.getStackTrace(e));
            return Pair.of(false, HttpEventResponse.builder()
                .statusCode(500)
                .body("Unknown error: " + e.getClass().getName())
                .header(ResponseHeader.ERROR.getHttpHeader(),"Unknown error")
                .build());
        }
    }
}
