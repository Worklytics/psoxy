package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.SQSOutput;
import co.worklytics.psoxy.aws.request.APIGatewayV2HTTPEventRequestAdapter;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.BatchMergeHandler;
import co.worklytics.psoxy.gateway.impl.InboundWebhookHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import co.worklytics.psoxy.aws.DaggerAwsContainer;

/**
 * AWS lambda entrypoint that can handle BOTH webhook from API Gateway / Function URL invocations AND SQS events.
 */
public class WebhookCollectionModeHandler implements RequestStreamHandler {

    /**
     * Static initialization allows reuse in containers
     * {@link "https://aws.amazon.com/premiumsupport/knowledge-center/lambda-improve-java-function-performance/"}
     */
    static AwsContainer awsContainer;

    static BatchMergeHandler batchMergeHandler;

    static InboundWebhookHandler inboundWebhookHandler;

    static ObjectMapper mapper;

    static {
        staticInit();
    }

    private static void staticInit() {
        awsContainer = DaggerAwsContainer.create();
        batchMergeHandler = awsContainer.batchMergeHandler();
        inboundWebhookHandler = awsContainer.inboundWebhookHandler();
        mapper = awsContainer.objectMapper();
    }


    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        // Read the full input stream into a tree
        JsonNode rootNode = mapper.readTree(input);

        if (isSQSEvent(rootNode)) {
            SQSEvent sqsEvent = mapper.treeToValue(rootNode, SQSEvent.class);
            handleRequest(sqsEvent, context);

            // Return empty 200 response
            APIGatewayV2HTTPResponse resp = APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withBody("SQS event processed")
                .build();
            mapper.writeValue(output, resp);
        } else if (isHttpEvent(rootNode)) {
            // API Gateway V2 HTTP event (from Function URL, usually)
            APIGatewayV2HTTPEvent httpEvent = mapper.treeToValue(rootNode, APIGatewayV2HTTPEvent.class);
            APIGatewayV2HTTPResponse response =handleRequest(httpEvent, context);
            mapper.writeValue(output, response);
        } else {
            context.getLogger().log("Unrecognized event format: " + rootNode.toString());
            APIGatewayV2HTTPResponse resp = APIGatewayV2HTTPResponse.builder()
                .withStatusCode(400)
                .withBody("Unsupported event type")
                .build();
            mapper.writeValue(output, resp);
        }
    }

    private boolean isSQSEvent(JsonNode node) {
        return node.has("Records") &&
            node.get("Records").isArray() &&
            node.get("Records").get(0).has("eventSource") &&
            "aws:sqs".equals(node.get("Records").get(0).get("eventSource").asText());
    }

    private boolean isHttpEvent(JsonNode node) {
        // TODO: consider possibility of a 1.0 format, which avoids url-decoding of path parameters

        return node.has("version") && node.get("version").asText().startsWith("2.0")
            && node.has("requestContext") && node.get("requestContext").has("http");
    }

    /**
     * SQS event is presumed to be a batch of messages that need to be merged into a single output object in the output
     *
     * @param sqsEvent the SQS event containing a batch of messages
     * @param context  of the lambda invocation
     */
    public void handleRequest(SQSEvent sqsEvent, Context context) {

        Stream<ProcessedContent> processedContentStream = sqsEvent.getRecords().stream().map(r -> ProcessedContent.builder()
            .contentType(r.getMessageAttributes().get(SQSOutput.MessageAttributes.CONTENT_TYPE).getStringValue())
            .contentEncoding(r.getMessageAttributes().get(SQSOutput.MessageAttributes.CONTENT_ENCODING).getStringValue())
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
        HttpEventResponse response;
        boolean base64Encoded = false;
        try {
            APIGatewayV2HTTPEventRequestAdapter httpEventRequestAdapter = new APIGatewayV2HTTPEventRequestAdapter(httpEvent);
            response = inboundWebhookHandler.handle(httpEventRequestAdapter);
        } catch (Throwable e) {
            context.getLogger().log(String.format("%s - %s", e.getClass().getName(), e.getMessage()));
            context.getLogger().log(ExceptionUtils.getStackTrace(e));
            response = HttpEventResponse.builder()
                .statusCode(500)
                .body("Unknown error: " + e.getClass().getName())
                .header(ResponseHeader.ERROR.getHttpHeader(),"Unknown error")
                .build();
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
}
