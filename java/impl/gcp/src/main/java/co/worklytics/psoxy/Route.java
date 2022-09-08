package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.CommonRequestHandler;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Log
public class Route implements HttpFunction {

    @Inject
    CommonRequestHandler requestHandler;

    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {

        CloudFunctionRequest cloudFunctionRequest = CloudFunctionRequest.of(request);

        //TODO: avoid rebuild graph everytime
        DaggerGcpContainer.create().injectRoute(this);

        HttpEventResponse abstractResponse =
                requestHandler.handle(cloudFunctionRequest);

        abstractResponse.getHeaders()
                .forEach(response::appendHeader);

        response.setStatusCode(abstractResponse.getStatusCode());

        if (abstractResponse.getBody() != null) {
            new ByteArrayInputStream(abstractResponse.getBody().getBytes(StandardCharsets.UTF_8))
                    .transferTo(response.getOutputStream());
        }
    }
}