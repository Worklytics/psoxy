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
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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


        boolean base64Encoded = false;

        // whatever the event is, convert to HttpEventRequest and invoke the request handler
        if (lambdaEventUtils.isSQSEvent(rootNode)) {
            JsonNode records = rootNode.get("Records");
            if (records == null || records.isEmpty()) {
                throw new IllegalArgumentException("SQS event has no records");
            }
            if (records.size() > 1) {
                // well, tbh - why have this limitation? we certainly could batch in future if there's a use case
                throw new IllegalArgumentException("SQS event has more than one record; in async case, we expect exactly one record per invocation");
            }
            rootNode = payloadMapper.readTree(records.get(0).get("body").asText());
        }

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
            response = requestHandler.handle(request);

            if (responseCompressionHandler.isCompressionRequested(request)) {
                Pair<Boolean, HttpEventResponse> compressedResponse = responseCompressionHandler.compressIfNeeded(response);
                base64Encoded = compressedResponse.getLeft();
                response = compressedResponse.getRight();
            }
        } catch (Throwable e) {

            context.getLogger().log(String.format("%s - %s", e.getClass().getName(), e.getMessage()));
            context.getLogger().log(ExceptionUtils.getStackTrace(e));
            response = HttpEventResponse.builder()
                .statusCode(500)
                .body("Unknown error: " + e.getClass().getName())
                .header(ResponseHeader.ERROR.getHttpHeader(),"Unknown error")
                .build();
        }

        if (lambdaEventUtils.isApiGatewayV1Event(rootNode)) {
            lambdaEventUtils.writeAsApiGatewayV1Response(response, output, base64Encoded);
        } else if (lambdaEventUtils.isApiGatewayV2Event(rootNode)) {
            lambdaEventUtils.writeAsApiGatewayV2Response(response, output, base64Encoded);
        } else {
            throw new IllegalArgumentException("Unsupported event type: " + rootNode.getNodeType());
        }
    }
}
