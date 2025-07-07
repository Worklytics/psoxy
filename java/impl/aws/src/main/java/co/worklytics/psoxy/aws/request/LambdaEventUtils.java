package co.worklytics.psoxy.aws.request;

import co.worklytics.psoxy.gateway.HttpEventResponse;
import com.amazonaws.services.lambda.runtime.events.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LambdaEventUtils {

    private final ObjectMapper lambdaEventMapper;

    @Inject
    public LambdaEventUtils(@Named("lambdaEventMapper") ObjectMapper lambdaEventMapper) {
        this.lambdaEventMapper = lambdaEventMapper;
    }


    public boolean isSQSEvent(JsonNode node) {
        return node.has("Records") &&
            node.get("Records").isArray() &&
            node.get("Records").get(0).has("eventSource") &&
            "aws:sqs".equals(node.get("Records").get(0).get("eventSource").asText());
    }

    public boolean isApiGatewayV1Event(JsonNode node) {
        // q: is this correct ???
        return node.has("version") && node.get("version").asText().startsWith("1.0")
            && node.has("requestContext") && node.get("requestContext").has("httpMethod");
    }

    // this is really API Gateway v2 *or* Lambda Function URL invocation
    public boolean isApiGatewayV2Event(JsonNode node) {
        return node.has("version") && node.get("version").asText().startsWith("2.0")
            && node.has("requestContext") && node.get("requestContext").has("http");
    }

    public JsonNode read(InputStream stream) throws IOException {
        return lambdaEventMapper.readTree(stream);
    }

    public APIGatewayProxyRequestEvent toAPIGatewayProxyRequestEvent(JsonNode event) throws JsonProcessingException {
        return lambdaEventMapper.treeToValue(event, APIGatewayProxyRequestEvent.class);
    }

    public APIGatewayV2HTTPEvent toAPIGatewayV2HTTPEvent(JsonNode event) throws JsonProcessingException {
        return lambdaEventMapper.treeToValue(event, APIGatewayV2HTTPEvent.class);
    }

    public SQSEvent toSQSEvent(JsonNode rootNode) throws JsonProcessingException {
        return lambdaEventMapper.treeToValue(rootNode, SQSEvent.class);
    }

    public void writeAsApiGatewayV1Response(HttpEventResponse response, OutputStream output, boolean base64Encoded) throws IOException {
        APIGatewayProxyResponseEvent apiGatewayResponse = new APIGatewayProxyResponseEvent()
            .withStatusCode(response.getStatusCode())
            .withHeaders(response.getHeaders())
            .withBody(response.getBody())
            .withIsBase64Encoded(base64Encoded);
        lambdaEventMapper.writeValue(output, apiGatewayResponse);
    }

    public void writeAsApiGatewayV2Response(HttpEventResponse response, OutputStream output, boolean base64Encoded) throws IOException {
        APIGatewayV2HTTPResponse apiGatewayResponse = APIGatewayV2HTTPResponse.builder()
            .withStatusCode(response.getStatusCode())
            .withHeaders(response.getHeaders())
            .withBody(response.getBody())
            .withIsBase64Encoded(base64Encoded)
            .build();
        lambdaEventMapper.writeValue(output, apiGatewayResponse);
    }

    /**
     * Writes an API Gateway V2 HTTP response to the given output stream.
     *
     * @param output   the output stream to write to
     * @param response the response to write
     * @throws IOException if an I/O error occurs
     */
    public void write(OutputStream output, APIGatewayV2HTTPResponse response) throws IOException {
        lambdaEventMapper.writeValue(output, response);
    }
}
