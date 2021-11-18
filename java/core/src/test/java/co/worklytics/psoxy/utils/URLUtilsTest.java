package co.worklytics.psoxy.utils;

import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.MalformedURLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class URLUtilsTest {

    @ValueSource(strings = {
        "https://www.domain.com/path/to/resource",
        "https://www.domain.com/path/to/resource?a=b",
        "https://www.domain.com/path/to/resource?a=b,c&b=c",
    })
    @ParameterizedTest
    @SneakyThrows
    public void relativeURL(String input) {
        assertEquals(input.replace("https://www.domain.com",""), URLUtils.relativeURL(input), "relative URL doesn't match");
    }

    @ValueSource(strings = {
        "www.domain.com/path/to/resource",
        "unknownprotocol://www.domain.com/path/to/resource?a=b",
    })
    @ParameterizedTest
    public void invalidURLThrowsException(String input) {
        assertThrows(MalformedURLException.class, () -> URLUtils.relativeURL(input), "shouldn't build URL");
    }
}
