package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.aws.LambdaRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.CommonRequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.util.Map;

@Log
public class Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<Map<String, Object>, String> {


    @Inject
    CommonRequestHandler requestHandler;

    @Inject
    ObjectMapper objectMapper;

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

        DaggerAwsContainer.create().injectHandler(this);

        HttpEventResponse response;
        try {
            String raw = objectMapper.writeValueAsString(request);

            LambdaRequest event = objectMapper.readerFor(LambdaRequest.class).readValue(raw);

            response = requestHandler.handle(event);
        } catch (Throwable e) {
            context.getLogger().log(e.getMessage());
            response = HttpEventResponse.builder()
                .statusCode(500)
                .body("Unknown error")
                .build();
        }

        try {
            return objectMapper.writer().writeValueAsString(response);
        } catch (Throwable e) {
            throw  new Error(e);
        }
    }
}
