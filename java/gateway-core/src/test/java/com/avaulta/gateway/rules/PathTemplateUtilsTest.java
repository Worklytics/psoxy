package com.avaulta.gateway.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PathTemplateUtilsTest {

    //test to clarify that leading `/` is expected, not assumed/optional, in path templates
    @CsvSource({
        "/bar,/bar,true",
        "bar,bar,true",
        "/bar,bar,false",
        "bar,/bar,false",
    })
    @ParameterizedTest
    void match(String template, String path, boolean matches) {
        PathTemplateUtils pathTemplateUtils = new PathTemplateUtils();
        assertEquals(matches, pathTemplateUtils.match(Map.of(template, "match"), path).isPresent());
    }
}
