package co.worklytics.psoxy.aws.request;

import co.worklytics.test.TestUtils;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

        assertFalse(requestAdapter.getQuery().isPresent());

        assertFalse(requestAdapter.getClientIp().isPresent());

        assertFalse(requestAdapter.isHttps().isPresent());
    }

    @SneakyThrows
    @Test
    public void parse_interesting() {

        APIGatewayProxyRequestEvent apiGatewayEvent = objectMapper.readerFor(APIGatewayProxyRequestEvent.class)
            .readValue(TestUtils.getData("lambda-proxy-events/api-gateway-v1-example_interesting.json"));

        APIGatewayV1ProxyEventRequestAdapter requestAdapter =
            APIGatewayV1ProxyEventRequestAdapter.of(apiGatewayEvent);

        assertEquals("/something", requestAdapter.getPath());

        assertTrue(requestAdapter.getQuery().isPresent());

        assertEquals("name=John", requestAdapter.getQuery().get());


        assertFalse(requestAdapter.isHttps().isPresent());
    }

    @SneakyThrows
    @Test
    public void parse_payload1_from_api_gateway_v2() {

        APIGatewayProxyRequestEvent apiGatewayEvent = objectMapper.readerFor(APIGatewayProxyRequestEvent.class)
            .readValue(TestUtils.getData("lambda-proxy-events/api-gateway-v2-payload-v1.json"));

        APIGatewayV1ProxyEventRequestAdapter requestAdapter =
            APIGatewayV1ProxyEventRequestAdapter.of(apiGatewayEvent);

        assertEquals("/v2/report/meetings/NUXghb123TCj0bP6nPVe%252Fsg253D253D/participants", requestAdapter.getPath());
    }

    @Test
    public void getClientIp_usesFirstForwardedForHopWhenSourceIpAbsent() {
        APIGatewayProxyRequestEvent apiGatewayEvent = new APIGatewayProxyRequestEvent()
            .withHeaders(Map.of("x-forwarded-for", "203.0.113.10, 198.51.100.7"));

        APIGatewayV1ProxyEventRequestAdapter requestAdapter =
            APIGatewayV1ProxyEventRequestAdapter.of(apiGatewayEvent);

        assertEquals("203.0.113.10", requestAdapter.getClientIp().get());
    }

    @SneakyThrows
    @Test
    public void getClientIp_prefersSourceIpOverForwardedHeader() {
        APIGatewayProxyRequestEvent.RequestIdentity identity =
            new APIGatewayProxyRequestEvent.RequestIdentity();
        identity.setSourceIp("198.51.100.7");

        APIGatewayProxyRequestEvent.ProxyRequestContext context =
            new APIGatewayProxyRequestEvent.ProxyRequestContext();
        context.setIdentity(identity);

        APIGatewayProxyRequestEvent apiGatewayEvent = new APIGatewayProxyRequestEvent()
            .withRequestContext(context)
            .withHeaders(Map.of("x-forwarded-for", "203.0.113.10"));

        APIGatewayV1ProxyEventRequestAdapter requestAdapter =
            APIGatewayV1ProxyEventRequestAdapter.of(apiGatewayEvent);

        assertEquals("198.51.100.7", requestAdapter.getClientIp().get());
    }
}
