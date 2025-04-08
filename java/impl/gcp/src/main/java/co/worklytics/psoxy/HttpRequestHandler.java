package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.CommonRequestHandler;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.RandomUtils;
import org.apache.http.HttpStatus;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

@Log
//@RequiredArgsConstructor(onConstructor_ = {@Inject})  //why doesn't this work
public class HttpRequestHandler {

    final CommonRequestHandler requestHandler;
    final EnvVarsConfigService envVarsConfigService;

    @Inject
    public HttpRequestHandler(CommonRequestHandler requestHandler, EnvVarsConfigService envVarsConfigService) {
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
                requestHandler.handle(cloudFunctionRequest);

            abstractResponse.getHeaders()
                .forEach(response::appendHeader);

            // sample 1% of requests, warning if compression not requested
            if (RandomUtils.nextInt(0, 99) == 0 && !cloudFunctionRequest.getWarnings().isEmpty()) {
                response.appendHeader(ResponseHeader.WARNING.getHttpHeader(),
                    Warning.COMPRESSION_NOT_REQUESTED.asHttpHeaderCode());
            }

            response.setStatusCode(abstractResponse.getStatusCode());

            if (abstractResponse.getBody() != null) {
                try (OutputStream outputStream = response.getOutputStream()) {
                    new ByteArrayInputStream(abstractResponse.getBody().getBytes(StandardCharsets.UTF_8))
                        .transferTo(response.getOutputStream());
                    outputStream.write(abstractResponse.getBody().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    log.log(Level.WARNING, "Error writing response body", e);
                }
            }
        } catch (Throwable e) {
            // unhandled exception while handling request
            log.log(Level.SEVERE, "Error while handling request", e);
            try {
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                response.appendHeader(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.UNKNOWN.name());
                response.getWriter().write("Unknown internal proxy error; review logs");
            } catch (IOException ioException) {
                log.log(Level.SEVERE, "Error writing error response", ioException);
            }
        }

    }
}
