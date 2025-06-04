package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.aws.request.APIGatewayV2HTTPEventRequestAdapter;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.InboundWebhookHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * handles lambda invocations where trigger is API Gateway V2 HTTP request event as incoming webhook
 *
 * use cases:
 *   - Lambda function URL invocations  (no actual API Gateway, but formats are the same)
 *   - Lambda function behind actual v2 API Gateway
 *
 *  this is what should be set as the entrypoint of the lambda function trigger
 */
@Log
public class APIGatewayV2HttpEventWebhookHandler implements com.amazonaws.services.lambda.runtime.RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    /**
     * Static initialization allows reuse in containers
     * {@link "https://aws.amazon.com/premiumsupport/knowledge-center/lambda-improve-java-function-performance/"}
     */
    static AwsContainer awsContainer;

    static InboundWebhookHandler inboundWebhookHandler;

    static {
        staticInit();
    }

    private static void staticInit() {
        awsContainer = DaggerAwsContainer.create();
        inboundWebhookHandler = awsContainer.inboundWebhookHandler();
    }

    @SneakyThrows
    @Override
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
