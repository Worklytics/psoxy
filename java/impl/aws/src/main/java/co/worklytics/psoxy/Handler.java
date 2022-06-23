package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.aws.request.APIGatewayV2HTTPEventRequestAdapter;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.CommonRequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.common.annotations.VisibleForTesting;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import io.opentracing.util.GlobalTracer;
import com.newrelic.opentracing.aws.LambdaTracing;
import com.newrelic.opentracing.LambdaTracer;

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

            context.getLogger().log(httpEventRequestAdapter.getHeader(HttpHeaders.ACCEPT_ENCODING).orElse("accept-encoding not found"));
            if (isCompressionRequested(httpEventRequestAdapter)) {
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

    private boolean isCompressionRequested(HttpEventRequest request) {
        return request.getHeader(HttpHeaders.ACCEPT_ENCODING).orElse("none").contains(ResponseCompressionHandler.GZIP);
    }

    static class ResponseCompressionHandler {

        static final String GZIP = "gzip";
        static final int DEFAULT_COMPRESSION_BUFFER_SIZE = 2048;
        static final int MIN_BYTES_TO_COMPRESS = 2048;

        @VisibleForTesting
        boolean compressionOutweighOverhead(String body) {
            return StringUtils.length(body) >= MIN_BYTES_TO_COMPRESS;
        }

        /**
         * Compresses content as binary base64
         * @param body
         * @return optional with content if compression has been applied
         */
        Optional<String> compressBodyAndConvertToBase64(String body) {
            if (compressionOutweighOverhead(body)) {
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream(DEFAULT_COMPRESSION_BUFFER_SIZE)) {
                    try (GZIPOutputStream output = new GZIPOutputStream(bos)) {
                        output.write(body.getBytes(StandardCharsets.UTF_8.name()));
                    }
                    return Optional.ofNullable(Base64.encodeBase64String(bos.toByteArray()));
                } catch (IOException ignored) {
                    // do nothing, send uncompressed
                }
            }
            return Optional.empty();
        }

        /**
         * @param response
         * @return (bool, response) - bool indicates if the response has been compressed or not
         */
        Pair<Boolean, HttpEventResponse> compressIfNeeded(HttpEventResponse response) {
            String uncompressed = response.getBody();
            Optional<String> compressedBody = compressBodyAndConvertToBase64(uncompressed);
            HttpEventResponse returnResponse = response;
            boolean compressed = compressedBody.isPresent();
            if (compressed) {
                returnResponse = HttpEventResponse.builder()
                    .body(compressedBody.get())
                    .statusCode(response.getStatusCode())
                    .headers(response.getHeaders())
                    .header(HttpHeaders.CONTENT_ENCODING, GZIP)
                    .build();
            }
            return Pair.of(compressed, returnResponse);
        }
    }

}
