package co.worklytics.psoxy;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.time.Instant;
import java.util.Base64;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.aws.request.APIGatewayV1ProxyEventRequestAdapter;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;


/**
 * handler to use for API Gateway V1 configurations
 *
 * usage: - when configure/deploy your lambda, set entry point to
 * `co.worklytics.psoxy.APIGatewayV1Handler` - in terraform, this is the `handler_class` variable -
 * https://github.com/Worklytics/psoxy/blob/main/infra/modules/aws-psoxy-rest/main.tf#L36 - under
 * Lambda --> Runtime Settings via AWS console
 */
public class APIGatewayV1Handler implements
        com.amazonaws.services.lambda.runtime.RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    /**
     * Static initialization allows reuse in containers
     * {@link "https://aws.amazon.com/premiumsupport/knowledge-center/lambda-improve-java-function-performance/"}
     */
    static AwsContainer awsContainer;
    static ApiDataRequestHandler requestHandler;

    static ResponseCompressionHandler responseCompressionHandler;

    static {
        staticInit();
    }

    private static void staticInit() {
        awsContainer = DaggerAwsContainer.create();
        requestHandler = awsContainer.apiDataRequestHandler();
        responseCompressionHandler = new ResponseCompressionHandler();
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent invocationEvent,
            Context context) {

        HttpEventResponse response;
        try {
            APIGatewayV1ProxyEventRequestAdapter httpEventRequestAdapter =
                    APIGatewayV1ProxyEventRequestAdapter.of(invocationEvent);
            response = requestHandler.handle(httpEventRequestAdapter,
                    ApiDataRequestHandler.ProcessingContext.builder().async(false)
                            .requestReceivedAt(Instant.ofEpochMilli(
                                    invocationEvent.getRequestContext().getRequestTimeEpoch()))
                            .requestId(invocationEvent.getRequestContext().getRequestId()).build());

            context.getLogger().log(httpEventRequestAdapter.getHeader(HttpHeaders.ACCEPT_ENCODING)
                    .orElse("accept-encoding not found"));
            if (responseCompressionHandler.isCompressionRequested(httpEventRequestAdapter)) {
                Pair<Boolean, HttpEventResponse> compressedResponse =
                        responseCompressionHandler.compressIfNeeded(response);
                response = compressedResponse.getRight();
            }

        } catch (Throwable e) {
            context.getLogger()
                    .log(String.format("%s - %s", e.getClass().getName(), e.getMessage()));
            context.getLogger().log(ExceptionUtils.getStackTrace(e));
            response = HttpEventResponse.builder().statusCode(500)
                    .bodyString("Unknown error: " + e.getClass().getName())
                    .header(ProcessedDataMetadataFields.ERROR.getHttpHeader(), "Unknown error")
                    .build();
        }

        try {
            // NOTE: AWS seems to give 502 Bad Gateway errors without explanation or any info
            // in the lambda logs if this is malformed somehow (Eg, missing statusCode)

            boolean base64Encode = response.getHeaders().getOrDefault(org.apache.hc.core5.http.HttpHeaders.CONTENT_ENCODING, "").equals("gzip");

            String bodyString = base64Encode ? Base64.getEncoder().encodeToString(response.getBody()) : new String(response.getBody());
            return new APIGatewayProxyResponseEvent().withStatusCode(response.getStatusCode())
                    .withHeaders(response.getHeaders())
                    .withMultiValueHeaders(response.getMultivaluedHeaders())
                    .withBody(bodyString)
                    .withIsBase64Encoded(base64Encode);
        } catch (Throwable e) {
            context.getLogger().log("Error writing response as Lambda return");
            throw new Error(e);
        }
    }
}
