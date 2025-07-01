package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.SQSOutput;
import co.worklytics.psoxy.aws.request.APIGatewayV2HTTPEventRequestAdapter;
import co.worklytics.psoxy.aws.request.LambdaEventUtils;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.BatchMergeHandler;
import co.worklytics.psoxy.gateway.impl.InboundWebhookHandler;
import co.worklytics.psoxy.gateway.impl.JwksDecorator;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
import org.apache.http.HttpStatus;

/**
 * AWS lambda entrypoint that can handle BOTH:
 *    - webhook requests from API Gateway / Function URL invocations, AND
 *    - SQS events (presumed to be batches of webhooks)
 */
@Log
public class AwsWebhookCollectionModeHandler implements RequestStreamHandler {

    /**
     * Static initialization allows reuse in containers
     * {@link "https://aws.amazon.com/premiumsupport/knowledge-center/lambda-improve-java-function-performance/"}
     */
    static AwsContainer awsContainer;

    static BatchMergeHandler batchMergeHandler;

    static InboundWebhookHandler inboundWebhookHandler;

    static JwksDecorator jwksHandler;

    static LambdaEventUtils lambdaEventUtils;

    static {
        staticInit();
    }

    private static void staticInit() {
        awsContainer = DaggerAwsContainer.create();
        batchMergeHandler = awsContainer.batchMergeHandler();
        inboundWebhookHandler = awsContainer.inboundWebhookHandler();
        jwksHandler = awsContainer.jwksDecoratorFactory().create(inboundWebhookHandler);
    }


    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        // Read the full input stream into a tree
        JsonNode rootNode = lambdaEventUtils.read(input);

        if (lambdaEventUtils.isApiGatewayV2Event(rootNode)) {
            // API Gateway V2 HTTP event (from Function URL, usually)
            APIGatewayV2HTTPEvent httpEvent = lambdaEventUtils.toAPIGatewayV2HTTPEvent(rootNode);
            APIGatewayV2HTTPResponse response = handleRequest(httpEvent, context);
            lambdaEventUtils.write(output, response);
        } else if (lambdaEventUtils.isSQSEvent(rootNode)) {
            SQSEvent sqsEvent = lambdaEventUtils.toSQSEvent(rootNode);
            handleRequest(sqsEvent, context);

            // Return empty 200 response
            APIGatewayV2HTTPResponse resp = APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withBody("SQS event processed")
                .build();
            lambdaEventUtils.write(output, resp);
        } else {
            context.getLogger().log("Unrecognized event format: " + rootNode.toString());
            APIGatewayV2HTTPResponse resp = APIGatewayV2HTTPResponse.builder()
                .withStatusCode(400)
                .withBody("Unsupported event type")
                .build();
            lambdaEventUtils.write(output, resp);
        }
    }



    /**
     * SQS event is presumed to be a batch of messages that need to be merged into a single output object in the output
     *
     * @param sqsEvent the SQS event containing a batch of messages
     * @param context  of the lambda invocation
     */
    public void handleRequest(SQSEvent sqsEvent, Context context) {

        Stream<ProcessedContent> processedContentStream = sqsEvent.getRecords().stream().map(r -> ProcessedContent.builder()
            .contentType(getStringValue(r.getMessageAttributes(), SQSOutput.MessageAttributes.CONTENT_TYPE))
            .contentEncoding(getStringValue(r.getMessageAttributes(), SQSOutput.MessageAttributes.CONTENT_ENCODING))
            .content(r.getBody().getBytes(StandardCharsets.UTF_8))
            .build());

        batchMergeHandler.handleBatch(processedContentStream);
    }

    /**
     * Handles lambda invocations where trigger is API Gateway V2 HTTP request event as incoming webhook
     *
     * @param httpEvent the API Gateway V2 HTTP event representing the incoming webhook request
     * @param context of the lambda invocation
     */
    @SneakyThrows
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent httpEvent, Context context) {
        //interfaces:
        // - HttpRequestEvent --> HttpResponseEvent

        HttpEventRequest httpEventRequest = new APIGatewayV2HTTPEventRequestAdapter(httpEvent);
        HttpEventResponse response;

        log.info("Request path: " + httpEventRequest.prettyPrint());

        boolean base64Encoded = false;
        if (httpEvent.getRequestContext().getRouteKey().endsWith("/.well-known/{proxy+}")) {
            // special case for JWKS endpoint, which is used by clients to fetch public keys
            // for verifying JWTs signed by the proxy
            response = jwksHandler.handle(httpEventRequest);
        } else {
            try {
                response = inboundWebhookHandler.handle(httpEventRequest);
            } catch (Throwable e) {
                context.getLogger().log(String.format("%s - %s", e.getClass().getName(), e.getMessage()));
                context.getLogger().log(ExceptionUtils.getStackTrace(e));
                response = HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .header(ResponseHeader.ERROR.getHttpHeader(), "Unknown error")
                    .body("Unknown error: " + e.getClass().getName())
                    .build();
            }
        }

        try {
            //NOTE: AWS seems to give 502 Bad Gateway errors without explanation or any info
            // in the lambda logs if this is malformed somehow (Eg, missing statusCode)
            return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(response.getStatusCode())
                .withHeaders(response.getHeaders())
                .withBody(response.getBody())
                .withIsBase64Encoded(base64Encoded)
                .build();
        } catch (Throwable e) {
            context.getLogger().log("Error writing response as Lambda return");
            throw new Error(e);
        }
    }

    private String getStringValue(Map<String, SQSEvent.MessageAttribute> messageAttributes, String key) {
        return Optional.ofNullable(messageAttributes.get(key))
            .map(SQSEvent.MessageAttribute::getStringValue)
            .orElse(null);
    }
}
