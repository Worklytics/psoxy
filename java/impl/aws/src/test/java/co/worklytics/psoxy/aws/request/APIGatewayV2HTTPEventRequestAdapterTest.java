package co.worklytics.psoxy.aws.request;

import co.worklytics.test.TestUtils;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class APIGatewayV2HTTPEventRequestAdapterTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @SneakyThrows
    @Test
    public void parse() {

        APIGatewayV2HTTPEvent apiGatewayV2HTTPEvent = objectMapper.readerFor(APIGatewayV2HTTPEvent.class)
            .readValue(TestUtils.getData("lambda-proxy-events/generic-request.json"));

        APIGatewayV2HTTPEventRequestAdapter requestAdapter = new APIGatewayV2HTTPEventRequestAdapter(apiGatewayV2HTTPEvent);

        assertEquals("/path/to/resource", requestAdapter.getPath());

        assertEquals("token=abc&page=2&multi=value2&multi=value1", requestAdapter.getQuery().orElse(null));
        assertEquals("application/json", requestAdapter.getHeader("content-type").orElse(null));

        assertTrue(requestAdapter.getMultiValueHeader("multi-header").isPresent());
        assertEquals("value1,value2", String.join(",", requestAdapter.getMultiValueHeader("multi-header").get()));
    }

}
