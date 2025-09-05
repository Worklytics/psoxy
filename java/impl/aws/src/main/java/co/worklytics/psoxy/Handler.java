package co.worklytics.psoxy;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.time.Instant;
import java.util.Base64;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hc.core5.http.HttpHeaders;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.aws.request.APIGatewayV2HTTPEventRequestAdapter;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

/**
 * default AWS lambda handler
 *
 * works with 1) lambda function URL invocations, or 2) API Gateway v2 HTTP proxy invocations
 *
 * TODO: in 0.6, rename this to AwsApiGatewayV2ApiDataRequestHandler, or something similar
 */
@Log
public class Handler implements
        com.amazonaws.services.lambda.runtime.RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

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

    @SneakyThrows
    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent httpEvent,
            Context context) {

        // interfaces:
        // - HttpRequestEvent --> HttpResponseEvent

        // q: what's the component?
        // - request handler?? but it's abstract ...
        // - make it bound with interface, rather than generic? --> prob best approach
        // - objectMapper
        //

        HttpEventResponse response;
        try {
            APIGatewayV2HTTPEventRequestAdapter httpEventRequestAdapter =
                    new APIGatewayV2HTTPEventRequestAdapter(httpEvent);
            response = requestHandler.handle(httpEventRequestAdapter,
                    ApiDataRequestHandler.ProcessingContext.builder().async(false)
                            .requestReceivedAt(Instant
                                    .ofEpochMilli(httpEvent.getRequestContext().getTimeEpoch()))
                            .requestId(httpEvent.getRequestContext().getRequestId()).build());

            //TODO: this is NOT AWS specific - move down into ApiDataRequestHandler
            if (!responseCompressionHandler.isCompressionRequested(httpEventRequestAdapter)) {
                response =
                    response.toBuilder()
                        .header(ProcessedDataMetadataFields.WARNING.getHttpHeader(),
                            Warning.COMPRESSION_NOT_REQUESTED.asHttpHeaderCode())
                        .build();
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

            // AWS can't deal with binary bodies; need to base64 encode in gzip case
            boolean base64Encode = response.getHeaders().getOrDefault(HttpHeaders.CONTENT_ENCODING, "").equals("gzip");

            return APIGatewayV2HTTPResponse.builder().withStatusCode(response.getStatusCode())
                    .withHeaders(response.getHeaders())
                    .withMultiValueHeaders(response.getMultivaluedHeaders())
                    .withBody(base64Encode ? Base64.getEncoder().encodeToString(response.getBody()) : new String(response.getBody(), StandardCharsets.UTF_8))
                    .withIsBase64Encoded(base64Encode)
                    .build();
        } catch (Throwable e) {
            context.getLogger().log("Error writing response as Lambda return");
            throw new Error(e);
        }
    }


}
