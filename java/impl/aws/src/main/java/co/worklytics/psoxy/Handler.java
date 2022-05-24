package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.aws.request.APIGatewayV2HTTPEventRequestAdapter;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.CommonRequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Log
public class Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    /**
     * Static initialization allows reuse in containers
     * {@link https://aws.amazon.com/premiumsupport/knowledge-center/lambda-improve-java-function-performance/}
     */
    static AwsContainer awsContainer;
    static CommonRequestHandler requestHandler;

    static {
        awsContainer = DaggerAwsContainer.create();
        requestHandler = awsContainer.createHandler();
    }

    @SneakyThrows
    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent httpEvent, Context context) {

        //interfaces:
        // - HttpRequestEvent --> HttpResponseEvent

        //q: what's the component?
        // - request handler?? but it's abstract ...
        //    - make it bound with interface, rather than generic? --> prob best approach
        // - objectMapper
        //

        HttpEventResponse response;
        try {
            response = requestHandler.handle(new APIGatewayV2HTTPEventRequestAdapter(httpEvent));
        } catch (Throwable e) {
            context.getLogger().log(String.format("%s - %s", e.getClass().getName(), e.getMessage()));
            context.getLogger().log(ExceptionUtils.getStackTrace(e));
            response = HttpEventResponse.builder()
                .statusCode(500)
                .body("Unknown error")
                .build();
        }

        try {
            return new APIGatewayV2HTTPResponse(
                response.getStatusCode(),
                response.getHeaders(),
                null, /* multi-valued headers */
                null, /* cookies */
                response.getBody(),
                false /* isBase64Encoded */
            );
        } catch (Throwable e) {
            context.getLogger().log("Error writing response as Lambda return");
            throw new Error(e);
        }
    }

}
