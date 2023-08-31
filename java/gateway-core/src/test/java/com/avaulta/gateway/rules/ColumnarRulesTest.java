package com.avaulta.gateway.rules;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ColumnarRulesTest {

    ObjectMapper yamlMapper = new YAMLMapper();

    final static String YAML = "---\n" +
        "columnsToPseudonymize:\n" +
        "- \"foo\"\n" +
        "columnsToRedact:\n" +
        "- \"bar\"\n" +
        "columnsToRename:\n" +
        "  a: \"b\"\n" +
        "delimiter: \",\"\n" +
        "pseudonymFormat: \"JSON\"\n"
        ;

    @SneakyThrows
    @Test
    public void yaml() {
        ColumnarRules example = ColumnarRules.builder()
            .columnToPseudonymize("foo")
            .columnToRedact("bar")
            .columnsToRename(Map.of("a", "b"))
            .build();

        assertEquals(YAML, yamlMapper.writeValueAsString(example));

        ColumnarRules roundtrip =
            yamlMapper.readerFor(ColumnarRules.class).readValue(yamlMapper.writeValueAsString(example));

        assertEquals(example.getColumnsToPseudonymize(), roundtrip.getColumnsToPseudonymize());
        assertEquals(example.getColumnsToRedact(), roundtrip.getColumnsToRedact());
        assertEquals(example.getColumnsToRename(), roundtrip.getColumnsToRename());
    }

    @SneakyThrows
    @Test
    public void yaml_format() {
        final String YAML_TOKENIZED = "---\n" +
            "columnsToPseudonymize:\n" +
            "- \"foo\"\n" +
            "columnsToRedact:\n" +
            "- \"bar\"\n" +
            "columnsToRename:\n" +
            "  a: \"b\"\n" +
            "delimiter: \",\"\n" +
            "pseudonymFormat: \"URL_SAFE_TOKEN\"\n"
            ;

        ColumnarRules example = ColumnarRules.builder()
            .pseudonymFormat(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .columnToPseudonymize("foo")
            .columnToRedact("bar")
            .columnsToRename(Map.of("a", "b"))
            .build();

        assertEquals(YAML_TOKENIZED, yamlMapper.writeValueAsString(example));

        ColumnarRules roundtrip =
            yamlMapper.readerFor(ColumnarRules.class).readValue(yamlMapper.writeValueAsString(example));

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
        ColumnarRules example = ColumnarRules.builder()
            .columnToRedact(nastyColumn)
            .columnsToRename(Map.of(nastyColumn, "niceColumn"))
            .build();


        ColumnarRules roundtrip =
            yamlMapper.readerFor(ColumnarRules.class).readValue(yamlMapper.writeValueAsString(example));

        assertEquals(example.getColumnsToPseudonymize(), roundtrip.getColumnsToPseudonymize());
        assertEquals(example.getColumnsToRedact(), roundtrip.getColumnsToRedact());
        assertEquals(example.getColumnsToRename(), roundtrip.getColumnsToRename());
    }

    @SneakyThrows
    @Test
    public void yaml_pipeline() {
        ColumnarRules rules = ColumnarRules.builder()
                .fieldsToTransform(Map.of("email", ColumnarRules.FieldTransformPipeline.builder()
                        .newName("github_username")
                        .transforms(Arrays.asList(
                                ColumnarRules.FieldValueTransform.filter(".*@worklytics.co"),
                                ColumnarRules.FieldValueTransform.formatString("%s_gh"),
                                ColumnarRules.FieldValueTransform.pseudonymizeWithScope("github")
                        )).build()))
                .build();

        String yaml = "---\n" +
                "delimiter: \",\"\n" +
                "fieldsToTransform:\n" +
                "  email:\n" +
                "    newName: \"github_username\"\n" +
                "    transforms:\n" +
                "    - filter: \".*@worklytics.co\"\n" +
                "    - formatString: \"%s_gh\"\n" +
                "    - pseudonymizeWithScope: \"github\"\n" +
                "pseudonymFormat: \"JSON\"\n";

        assertEquals(yaml,
                yamlMapper.writeValueAsString(rules));

    }

}
