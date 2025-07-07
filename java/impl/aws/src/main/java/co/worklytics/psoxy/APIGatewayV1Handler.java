package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.aws.request.APIGatewayV1ProxyEventRequestAdapter;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.time.Instant;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;


/**
 * handler to use for API Gateway V1 configurations
 *
 * usage:
 *  - when configure/deploy your lambda, set entry point to `co.worklytics.psoxy.APIGatewayV1Handler`
 *  - in terraform, this is the `handler_class` variable
 *     - https://github.com/Worklytics/psoxy/blob/main/infra/modules/aws-psoxy-rest/main.tf#L36
 *     - under Lambda --> Runtime Settings via AWS console
 */
public class APIGatewayV1Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

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
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {

        HttpEventResponse response;
        boolean base64Encoded = false;
        try {
            APIGatewayV1ProxyEventRequestAdapter httpEventRequestAdapter = APIGatewayV1ProxyEventRequestAdapter.of(input);
            response = requestHandler.handle(httpEventRequestAdapter, ApiDataRequestHandler.ProcessingContext.builder()
                    .async(false)
                .requestReceivedAt(Instant.parse(input.getRequestContext().getRequestTime()))
                .requestId(input.getRequestContext().getRequestId())
                .build());

            context.getLogger().log(httpEventRequestAdapter.getHeader(HttpHeaders.ACCEPT_ENCODING).orElse("accept-encoding not found"));
            if (responseCompressionHandler.isCompressionRequested(httpEventRequestAdapter)) {
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
                .header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),"Unknown error")
                .build();
        }

        try {
            //NOTE: AWS seems to give 502 Bad Gateway errors without explanation or any info
            // in the lambda logs if this is malformed somehow (Eg, missing statusCode)

            return new APIGatewayProxyResponseEvent()
                .withStatusCode(response.getStatusCode())
                .withHeaders(response.getHeaders())
                .withBody(response.getBody())
                .withIsBase64Encoded(base64Encoded);
        } catch (Throwable e) {
            context.getLogger().log("Error writing response as Lambda return");
            throw new Error(e);
        }
    }
}
