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
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import co.worklytics.psoxy.aws.DaggerAwsContainer;

/**
 * AWS lambda entrypoint that can handle BOTH:
 *    - webhook requests from API Gateway / Function URL invocations, AND
 *    - SQS events (presumed to be batches of webhooks)
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

    static ObjectMapper sqsPayloadMapper;

    static {
        staticInit();
    }

    private static void staticInit() {
        awsContainer = DaggerAwsContainer.create();
        batchMergeHandler = awsContainer.batchMergeHandler();
        inboundWebhookHandler = awsContainer.inboundWebhookHandler();
        mapper = awsContainer.objectMapper();
        sqsPayloadMapper = new ObjectMapper();
        sqsPayloadMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }


    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {

        // should we cut this? no auth checks done yet, so potentially large unauthorized payload could be DoS vector
        String body = IOUtils.toString(input, StandardCharsets.UTF_8);


        // try for incoming webhook event first; it's the more common and performance sensitive case
        Optional<APIGatewayV2HTTPEvent> httpEvent = tryReadHttpEvent(mapper, body);
        if (httpEvent.isPresent()) {
            APIGatewayV2HTTPResponse response = handleRequest(httpEvent.get(), context);
            mapper.writeValue(output, response);
            return;
        }

        Optional<SQSEvent> sqsEvent = tryReadSQSEvent(sqsPayloadMapper, body);
        if (sqsEvent.isPresent()) {
            handleRequest(sqsEvent.get(), context);

            // Return empty 200 response
            APIGatewayV2HTTPResponse resp = APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withBody("SQS event processed")
                .build();
            mapper.writeValue(output, resp);
            return;
        }


        context.getLogger().log("Unrecognized event format: " +body);
        APIGatewayV2HTTPResponse resp = APIGatewayV2HTTPResponse.builder()
            .withStatusCode(400)
            .withBody("Unsupported event type")
            .build();
        mapper.writeValue(output, resp);
    }

    @VisibleForTesting
    Optional<SQSEvent> tryReadSQSEvent(ObjectMapper mapper, String input) throws IOException {
        try {
            return Optional.of(mapper.readerFor(SQSEvent.class).readValue(input));
        } catch (Exception e) {
            // If reading as SQSEvent fails, we assume it's not an SQS event
            return Optional.empty();
        }
    }

    @VisibleForTesting
    Optional<APIGatewayV2HTTPEvent> tryReadHttpEvent(ObjectMapper mapper, String input) throws IOException {
        try {
            return Optional.of(mapper.readerFor(APIGatewayV2HTTPEvent.class).readValue(input));
        } catch (Exception e) {
            // If reading as APIGatewayV2HTTPEvent fails, we assume it's not an HTTP event
            return Optional.empty();
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

    private String getStringValue(Map<String, SQSEvent.MessageAttribute> messageAttributes, String key) {
        return Optional.ofNullable(messageAttributes.get(key))
            .map(SQSEvent.MessageAttribute::getStringValue)
            .orElse(null);
    }
}
