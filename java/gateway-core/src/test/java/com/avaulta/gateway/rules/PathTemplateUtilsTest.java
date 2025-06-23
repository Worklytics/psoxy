package com.avaulta.gateway.rules;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @CsvSource({
        "/{foo}/,/bar/,bar",
        "/blah/{foo},/blah/bar,bar",
        "/{foo}/baz,/bar/baz,bar",
        "/blah/{foo}/baz,/blah/bar/baz,bar",
    })
    @ParameterizedTest
    public void capturesCorrectly(String template, String path, String expected) {
        PathTemplateUtils pathTemplateUtils = new PathTemplateUtils();
        String regex = pathTemplateUtils.asRegex(template);
        Matcher matcher = Pattern.compile(regex).matcher(path);
        assertTrue(matcher.matches());
        assertEquals(expected, matcher.group("foo"));
    }

    @CsvSource({
        "/{foo}/,//",
        "/blah/{foo},/blah/",
        "/{foo}/baz,//baz",
        "/blah/{foo}/baz,/blah//baz",
    })
    @ParameterizedTest
    public void doesntCaptureEmpty(String template, String path) {
        PathTemplateUtils pathTemplateUtils = new PathTemplateUtils();
        String regex = pathTemplateUtils.asRegex(template);
        Matcher matcher = Pattern.compile(regex).matcher(path);
        assertFalse(matcher.matches());
    }
}
