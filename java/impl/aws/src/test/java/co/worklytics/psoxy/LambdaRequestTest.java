package co.worklytics.psoxy;

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
            .readValue(TestUtils.getData("request.json"));

        assertEquals("/path/to/resource", request.getPath());

        assertEquals("bar", request.getQueryParameters().get("foo").get(0));

        assertEquals("gzip, deflate, sdch", request.getHeaders().get("Accept-Encoding").get(0));
    }

}
