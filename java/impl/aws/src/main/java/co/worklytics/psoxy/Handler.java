package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.AwsModule;
import co.worklytics.psoxy.aws.LambdaRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.AbstractRequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Component;
import lombok.*;
import lombok.extern.java.Log;

import java.util.Map;

@Log
public class Handler implements RequestHandler<Map<String, Object>, String> {

    @Component(modules = {
        AwsModule.class,
        CoreModule.class,
    })
    interface AwsComponent {

        ObjectMapper objectMapper();

        AbstractRequestHandler requestHandler();
    }

    //TODO: improve this
    //   - change this to directly take LambdaRequest as it's parameter type
    //   - or to take InputStream, and then parse that with jackson (AWS lambda docs implies this is supported, but doesn't work)
    @SneakyThrows
    @Override
    public String handleRequest(Map<String, Object> request, Context context) {

        //interfaces:
        // - HttpRequestEvent --> HttpResponseEvent

        //q: what's the component?
        // - request handler?? but it's abstract ...
        //    - make it bound with interface, rather than generic? --> prob best approach
        // - objectMapper
        //

        AwsComponent graph = DaggerHandler_AwsComponent.builder().build();

        HttpEventResponse response;
        try {
            String raw = graph.objectMapper().writeValueAsString(request);

            LambdaRequest event = graph.objectMapper().readerFor(LambdaRequest.class).readValue(raw);

            response = graph.requestHandler().handle(event);
        } catch (Throwable e) {
            context.getLogger().log(e.getMessage());
            response = HttpEventResponse.builder()
                .statusCode(500)
                .body("Unknown error")
                .build();
        }

        try {
            return graph.objectMapper().writer().writeValueAsString(response);
        } catch (Throwable e) {
            throw  new Error(e);
        }
    }
}
