package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.CommonRequestHandler;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import lombok.extern.java.Log;
import org.apache.commons.lang3.RandomUtils;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

@Log
public class Route implements HttpFunction {

    @Inject
    CommonRequestHandler requestHandler;
    @Inject
    EnvVarsConfigService envVarsConfigService;


    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {

        injectDependenciesIfNeeded();

        CloudFunctionRequest cloudFunctionRequest = CloudFunctionRequest.of(request);

        try {
            if (envVarsConfigService.isDevelopment()) {
                cloudFunctionRequest.getWarnings().forEach(log::warning);
            }
        } catch (Throwable e) {
            //suppress anything that went wrong here
            log.log(Level.WARNING, "Throwable while computing warnings that is suppressed", e);
        }

        HttpEventResponse abstractResponse =
                requestHandler.handle(cloudFunctionRequest);

        abstractResponse.getHeaders()
                .forEach(response::appendHeader);

        //sample 1% of requests, warning if compression not requested
        if (RandomUtils.nextInt(0, 99) == 0 && !cloudFunctionRequest.getWarnings().isEmpty()) {
            response.appendHeader(ResponseHeader.WARNING.getHttpHeader(),
                Warning.COMPRESSION_NOT_REQUESTED.asHttpHeaderCode());
        }

        response.setStatusCode(abstractResponse.getStatusCode());

        if (abstractResponse.getBody() != null) {
            new ByteArrayInputStream(abstractResponse.getBody().getBytes(StandardCharsets.UTF_8))
                    .transferTo(response.getOutputStream());
        }
    }

    void injectDependenciesIfNeeded() {
        if (envVarsConfigService == null) {
            synchronized (this) {
                if (envVarsConfigService == null) {
                    GcpContainer gcpContainer = DaggerGcpContainer.create();
                    gcpContainer.injectRoute(this);
                }
            }
        }
    }

}
