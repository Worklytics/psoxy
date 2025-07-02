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
import org.apache.commons.lang3.tuple.Pair;
import io.opentracing.util.GlobalTracer;
import com.newrelic.opentracing.aws.LambdaTracing;
import com.newrelic.opentracing.LambdaTracer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

/**
 * default AWS lambda handler
 *
 * works with 1) lambda function URL invocations, or 2) API Gateway v2 HTTP proxy invocations
 *
 */
@Log
public class Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    /**
     * Static initialization allows reuse in containers
     * {@link "https://aws.amazon.com/premiumsupport/knowledge-center/lambda-improve-java-function-performance/"}
     */
    static AwsContainer awsContainer;
    static CommonRequestHandler requestHandler;

    static ResponseCompressionHandler responseCompressionHandler;

    static {
        staticInit();
    }

    private static void staticInit() {
        awsContainer = DaggerAwsContainer.create();
        requestHandler = awsContainer.createHandler();
        //TODO: move into DI??
        // Register the New Relic OpenTracing LambdaTracer as the Global Tracer
        GlobalTracer.registerIfAbsent(LambdaTracer.INSTANCE);
        responseCompressionHandler = new ResponseCompressionHandler();

        Security.addProvider(new BouncyCastleProvider());
    }

    @SneakyThrows
    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent httpEvent, Context context) {
        return LambdaTracing.instrument(httpEvent, context, this::actualHandleRequest);
    }


    public APIGatewayV2HTTPResponse actualHandleRequest(APIGatewayV2HTTPEvent httpEvent, Context context) {
        //interfaces:
        // - HttpRequestEvent --> HttpResponseEvent

        //q: what's the component?
        // - request handler?? but it's abstract ...
        //    - make it bound with interface, rather than generic? --> prob best approach
        // - objectMapper
        //

        HttpEventResponse response;
        boolean base64Encoded = false;
        try {
            APIGatewayV2HTTPEventRequestAdapter httpEventRequestAdapter = new APIGatewayV2HTTPEventRequestAdapter(httpEvent);
            response = requestHandler.handle(httpEventRequestAdapter);

            if (ResponseCompressionHandler.isCompressionRequested(httpEventRequestAdapter)) {
                Pair<Boolean, HttpEventResponse> compressedResponse = responseCompressionHandler.compressIfNeeded(response);
                base64Encoded = compressedResponse.getLeft();
                response = compressedResponse.getRight();
            } else {
                response = response.toBuilder()
                    .header(ResponseHeader.WARNING.getHttpHeader(), Warning.COMPRESSION_NOT_REQUESTED.asHttpHeaderCode())
                    .build();
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
