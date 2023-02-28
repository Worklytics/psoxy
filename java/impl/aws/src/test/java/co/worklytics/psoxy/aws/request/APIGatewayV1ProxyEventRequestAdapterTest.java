package co.worklytics.psoxy.aws.request;

import co.worklytics.test.TestUtils;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class APIGatewayV1ProxyEventRequestAdapterTest {


    ObjectMapper objectMapper = new ObjectMapper();

    @SneakyThrows
    @Test
    public void parse() {

        APIGatewayProxyRequestEvent apiGatewayEvent = objectMapper.readerFor(APIGatewayProxyRequestEvent.class)
            .readValue(TestUtils.getData("lambda-proxy-events/api-gateway-v1-example.json"));

        APIGatewayV1ProxyEventRequestAdapter requestAdapter =
            APIGatewayV1ProxyEventRequestAdapter.of(apiGatewayEvent);

        assertEquals("/", requestAdapter.getPath());
    }
}
