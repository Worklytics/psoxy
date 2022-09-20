package co.worklytics.psoxy.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CsvRulesTest {

    ObjectMapper yamlMapper = new YAMLMapper();

    final static String YAML = "---\n" +
        "columnsToPseudonymize:\n" +
        "- \"foo\"\n" +
        "columnsToRedact:\n" +
        "- \"bar\"\n" +
        "columnsToRename:\n" +
        "  a: \"b\"\n";

    @SneakyThrows
    @Test
    public void yaml() {
        CsvRules example = CsvRules.builder()
            .columnToPseudonymize("foo")
            .columnToRedact("bar")
            .columnsToRename(ImmutableMap.of("a", "b"))
            .build();

        assertEquals(YAML, yamlMapper.writeValueAsString(example));

        CsvRules roundtrip =
            yamlMapper.readerFor(CsvRules.class).readValue(yamlMapper.writeValueAsString(example));

        assertEquals(example.getColumnsToPseudonymize(), roundtrip.getColumnsToPseudonymize());
        assertEquals(example.getColumnsToRedact(), roundtrip.getColumnsToRedact());
        assertEquals(example.getColumnsToRename(), roundtrip.getColumnsToRename());
    }

    @SneakyThrows
    @ValueSource(strings = {
        "space asf",
        "  untrimmed spaces ",
        " quote's",
        "comma,s",
    })
    @ParameterizedTest
    public void yamlWithNastyColumns(String nastyColumn) {
        CsvRules example = CsvRules.builder()
            .columnToRedact(nastyColumn)
            .columnsToRename(ImmutableMap.of(nastyColumn, "niceColumn"))
            .build();


        CsvRules roundtrip =
            yamlMapper.readerFor(CsvRules.class).readValue(yamlMapper.writeValueAsString(example));

        assertEquals(example.getColumnsToPseudonymize(), roundtrip.getColumnsToPseudonymize());
        assertEquals(example.getColumnsToRedact(), roundtrip.getColumnsToRedact());
        assertEquals(example.getColumnsToRename(), roundtrip.getColumnsToRename());
    }

}
