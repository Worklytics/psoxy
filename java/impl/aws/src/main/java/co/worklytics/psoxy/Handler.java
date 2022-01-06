package co.worklytics.psoxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.*;

public class Handler implements RequestHandler<Map<String,String>, String> {

    ObjectMapper mapper = new ObjectMapper();

    @SneakyThrows
    @Override
    public String handleRequest(Map<String,String> event, Context context)
    {
        LambdaLogger logger = context.getLogger();
        String response = new String("200 OK");
        // log execution details
        logger.log("ENVIRONMENT VARIABLES: " + mapper.writeValueAsString(System.getenv()));
        logger.log("CONTEXT: " + mapper.writeValueAsString(context));
        // process event
        logger.log("EVENT: " + mapper.writeValueAsString(event));
        logger.log("EVENT TYPE: " + event.getClass().toString());
        return response;
    }
}
