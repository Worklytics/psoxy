package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.CommonRequestHandler;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import dagger.Component;
import lombok.extern.java.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Log
public class Route implements HttpFunction {


    @Component(modules = GcpModule.class)
    interface GcpComponent {

        CommonRequestHandler requestHandler();
    }

    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {

        GcpComponent graph = DaggerRoute_GcpComponent.builder().build();

        HttpEventResponse abstractResponse =
            graph.requestHandler().handle(CloudFunctionRequest.of(request));

        abstractResponse.getHeaders()
                .forEach(h -> response.appendHeader(h.getKey(), h.getValue()));

        response.setStatusCode(abstractResponse.getStatusCode());
        new ByteArrayInputStream(abstractResponse.getBody().getBytes(StandardCharsets.UTF_8))
            .transferTo(response.getOutputStream());
    }

}
