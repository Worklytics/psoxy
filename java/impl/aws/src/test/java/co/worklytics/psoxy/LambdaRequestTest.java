package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.LambdaRequest;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.test.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LambdaRequestTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @SneakyThrows
    @Test
    public void parse() {

        LambdaRequest request = objectMapper.readerFor(LambdaRequest.class)
            .readValue(TestUtils.getData("lambda-proxy-events/generic-request.json"));

        assertEquals("/path/to/resource", request.getPath());

        assertEquals("bar", request.getQueryParameters().get("foo").get(0));
        assertEquals("foo=bar", request.getQuery().get());

        assertEquals("gzip, deflate, sdch", request.getHeaders().get("Accept-Encoding").get(0));

    }

    //test real example payload, which can be used to directly test psoxy behavior in AWS console
    // (eg, on page like https://console.aws.amazon.com/lambda/home?region=us-east-1#/functions/psoxy-gdirectory?tab=testing )
    @SneakyThrows
    @Test
    public void asEventRequest() {
        HttpEventRequest request = (HttpEventRequest) objectMapper.readerFor(LambdaRequest.class)
            .readValue(TestUtils.getData("lambda-proxy-events/gdirectory-proxy-example.json"));

        assertEquals("/admin/directory/v1/customer/my_customer/domains",
            request.getPath());

        assertTrue(!request.getQuery().isPresent());

        assertEquals("erik@worklytics.co",
            request.getHeader("X-Psoxy-User-To-Impersonate").get().get(0));
    }

}
