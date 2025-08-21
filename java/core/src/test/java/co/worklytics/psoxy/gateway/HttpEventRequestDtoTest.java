package co.worklytics.psoxy.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpEventRequestDtoTest {


    @SneakyThrows
    @Test
    void testJsonRoundtrip() {
        ObjectMapper objectMapper = new ObjectMapper();

        HttpEventRequestDto dto = HttpEventRequestDto.builder()
            .path("/test/path")
            .query("param=value")
            .httpMethod("GET")
            .body("test body".getBytes())
            .clientIp("123.456.789.012")
            .headers(Map.of("Content-Type", List.of("application/json")))
            .https(true)
            .build();

        String json = objectMapper.writeValueAsString(dto);

        HttpEventRequestDto deserializedDto = objectMapper.readValue(json, HttpEventRequestDto.class);
        assertEquals(dto.getPath(), deserializedDto.getPath());
        assertEquals(dto.getQuery().get(), deserializedDto.getQuery().get());
        assertEquals(dto.getHttpMethod(), deserializedDto.getHttpMethod());
        assertArrayEquals(dto.getBody(), deserializedDto.getBody());
        assertEquals(dto.getClientIp().get(), deserializedDto.getClientIp().orElse(null));
        assertEquals(dto.getHeaders(), deserializedDto.getHeaders());
        assertEquals(dto.getHttps(), deserializedDto.getHttps());
    }

}
