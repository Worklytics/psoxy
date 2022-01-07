package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.impl.AbstractRequestHandler;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import lombok.extern.java.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Log
public class Route implements HttpFunction {

    AbstractRequestHandler<HttpRequest> requestHandler
        = new AbstractRequestHandler(new CloudFunctionRequestAdapter());


    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {

        AbstractRequestHandler.Response abstractResponse = requestHandler.handle(request);

        abstractResponse.getHeaders()
                .forEach(h -> response.appendHeader(h.getKey(), h.getValue()));

        response.setStatusCode(abstractResponse.getStatusCode());
        new ByteArrayInputStream(abstractResponse.getBody().getBytes(StandardCharsets.UTF_8))
            .transferTo(response.getOutputStream());
    }

}
