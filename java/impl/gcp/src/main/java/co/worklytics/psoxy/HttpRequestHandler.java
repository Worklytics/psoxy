package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import lombok.extern.java.Log;
import org.apache.commons.lang3.RandomUtils;
import org.apache.http.HttpStatus;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;

/**
 * TODO: rename this to GcpApiDataRequestHandler, or something similar in 0.6+
 */
@Log
//@RequiredArgsConstructor(onConstructor_ = {@Inject})  //why doesn't this work
public class HttpRequestHandler {

    final ApiDataRequestHandler requestHandler;
    final EnvVarsConfigService envVarsConfigService;

    @Inject
    public HttpRequestHandler(ApiDataRequestHandler requestHandler, EnvVarsConfigService envVarsConfigService) {
        this.requestHandler = requestHandler;
        this.envVarsConfigService = envVarsConfigService;
    }

    public void service(HttpRequest request, HttpResponse response) {

        CloudFunctionRequest cloudFunctionRequest = CloudFunctionRequest.of(request);

        try {
            if (envVarsConfigService.isDevelopment()) {
                cloudFunctionRequest.getWarnings().forEach(log::warning);
            }
        } catch (Throwable e) {
            //suppress anything that went wrong here
            log.log(Level.WARNING, "Throwable while computing warnings that is suppressed", e);
        }

        try {
            HttpEventResponse abstractResponse =
                requestHandler.handle(cloudFunctionRequest, ApiDataRequestHandler.ProcessingContext.builder()
                    .async(false)
                    .requestId(UUID.randomUUID().toString())
                    .requestReceivedAt(Instant.now())
                    .build());

            fillGcpResponseFromGenericResponse(response, abstractResponse);

            // sample 1% of requests, warning if compression not requested
            if (RandomUtils.nextInt(0, 99) == 0 && !cloudFunctionRequest.getWarnings().isEmpty()) {
                response.appendHeader(ProcessedDataMetadataFields.WARNING.getHttpHeader(),
                    Warning.COMPRESSION_NOT_REQUESTED.asHttpHeaderCode());
            }
        } catch (Throwable e) {
            // unhandled exception while handling request
            log.log(Level.SEVERE, "Error while handling request", e);
            try {
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                response.appendHeader(ProcessedDataMetadataFields.ERROR.getHttpHeader(), ErrorCauses.UNKNOWN.name());
                response.getWriter().write("Unknown internal proxy error; review logs");
            } catch (IOException ioException) {
                log.log(Level.SEVERE, "Error writing error response", ioException);
            }
        }
    }

    static void fillGcpResponseFromGenericResponse(HttpResponse response, HttpEventResponse genericResponse) throws IOException {
        response.setStatusCode(genericResponse.getStatusCode());

        genericResponse.getHeaders().forEach(response::appendHeader);
        genericResponse.getMultivaluedHeaders().forEach((key, valueList) -> valueList.forEach(value -> response.appendHeader(key, value)));

        if (genericResponse.getBody() != null) {
            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(genericResponse.getBody().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.log(Level.WARNING, "Error writing response body", e);
            }
        }
    }
}
