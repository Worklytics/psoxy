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

    // --- optional param {foo?} tests ---

    @CsvSource({
        // optional param matches non-empty value
        "/blah/{foo?},/blah/bar,true",
        // optional param matches empty value
        "/blah/{foo?},/blah/,true",
        // optional param at end, no trailing slash, matches empty
        "/blah{foo?},/blah,true",
        // optional param at end, matches non-empty
        "/blah{foo?},/blah.gz,true",
        // required + optional mixed: both present
        "/{req}/data{opt?},{req}/data.gz,false",
        // leading slash matters even with optional params
        "/{req}/data{opt?},export/data,false",
    })
    @ParameterizedTest
    void matchOptional(String template, String path, boolean matches) {
        PathTemplateUtils pathTemplateUtils = new PathTemplateUtils();
        assertEquals(matches, pathTemplateUtils.match(Map.of(template, "match"), path).isPresent());
    }

    @CsvSource({
        // captures non-empty optional value
        "/blah{foo?},/blah.gz,.gz",
        // captures empty optional value
        "/blah{foo?},/blah,''",
    })
    @ParameterizedTest
    public void capturesOptionalCorrectly(String template, String path, String expected) {
        PathTemplateUtils pathTemplateUtils = new PathTemplateUtils();
        String regex = pathTemplateUtils.asRegex(template);
        Matcher matcher = Pattern.compile(regex).matcher(path);
        assertTrue(matcher.matches(), "Expected template '" + template + "' to match path '" + path + "' with regex: " + regex);
        assertEquals(expected, matcher.group("foo"));
    }

    // real-world use case: file paths with optional .gz suffix
    @CsvSource({
        // matches .ndjson (no suffix)
        "{exportId}/events{shardIndex}.ndjson{suffix?},export123/events0-1775063099227.ndjson,true",
        // matches .ndjson.gz
        "{exportId}/events{shardIndex}.ndjson{suffix?},export123/events0-1775019339023.ndjson.gz,true",
        // doesn't match wrong base name
        "{exportId}/events{shardIndex}.ndjson{suffix?},export123/items0.ndjson,false",
    })
    @ParameterizedTest
    void matchFilePathWithOptionalSuffix(String template, String path, boolean matches) {
        PathTemplateUtils pathTemplateUtils = new PathTemplateUtils();
        assertEquals(matches, pathTemplateUtils.match(Map.of(template, "match"), path).isPresent());
    }
}
