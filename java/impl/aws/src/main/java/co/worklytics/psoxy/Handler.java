package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.impl.AbstractRequestHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

public class Handler implements RequestHandler<LambdaRequest, String> {

    ObjectMapper mapper = new ObjectMapper();

    AbstractRequestHandler<LambdaRequest> requestHandler =
        new AbstractRequestHandler<>(new LambdaRequestAdapter());

    @SneakyThrows
    @Override
    public String handleRequest(LambdaRequest event, Context context) {
        AbstractRequestHandler.Response response = requestHandler.handle(event);

        return mapper.writer().writeValueAsString(response);
    }
}
