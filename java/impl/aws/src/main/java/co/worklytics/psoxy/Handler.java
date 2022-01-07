package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.impl.AbstractRequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.util.Map;

public class Handler implements RequestHandler<Map<String, Object>, String> {

    ObjectMapper mapper = new ObjectMapper();

    AbstractRequestHandler<LambdaRequest> requestHandler =
        new AbstractRequestHandler<>(new LambdaRequestAdapter());

    //TODO: improve this
    //   - change this to directly take LambdaRequest as it's parameter type
    //   - or to take InputStream, and then parse that with jackson (AWS lambda docs implies this is supported, but doesn't work)
    @SneakyThrows
    @Override
    public String handleRequest(Map<String, Object> request, Context context) {

        String raw = mapper.writeValueAsString(request);

        LambdaRequest event = mapper.readerFor(LambdaRequest.class).readValue(raw);

        AbstractRequestHandler.Response response = requestHandler.handle(event);

        return mapper.writer().writeValueAsString(response);
    }
}
