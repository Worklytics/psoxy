package com.avaulta.gateway.rules;

import com.avaulta.gateway.rules.transforms.RecordTransform;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiTypeBulkDataRulesTest {


    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @SneakyThrows
    @Test
    public void fromYaml() {

        InputStream yamlStream = getClass().getClassLoader().getResourceAsStream("rules/multi-type-example.yaml");


        MultiTypeBulkDataRules rules = yamlMapper.readerFor(MultiTypeBulkDataRules.class).readValue(yamlStream);

        // Basic checks to ensure deserialization worked (you can add more specific checks)
        assertNotNull(rules);
        RecordRules record = (RecordRules) rules.getFileRules().get("/export/{week}/index_{shard}.ndjson");
        assertEquals(RecordRules.Format.valueOf("NDJSON"), record.getFormat());
        assertNotNull(record);
        assertFalse(record.getTransforms().isEmpty());
    }

    @SneakyThrows
    @Test
    public void toYaml() {

        final String EXPECTED = "---\n" +
            "fileRules:\n" +
            "  /export/{week}/data_{shard}.csv:\n" +
            "    columnsToPseudonymize:\n" +
            "    - \"email\"\n" +
            "    delimiter: \",\"\n" +
            "    pseudonymFormat: \"JSON\"\n" +
            "  /export/{week}/index_{shard}.ndjson:\n" +
            "    format: \"NDJSON\"\n" +
            "    transforms:\n" +
            "    - redact: \"foo\"\n" +
            "    - pseudonymize: \"bar\"\n";

        RecordRules recordRules = RecordRules.builder()
            .transform(RecordTransform.Redact.builder()
                .redact("foo")
                .build())
            .transform(RecordTransform.Pseudonymize.builder()
                .pseudonymize("bar")
                .build())
            .build();

        ColumnarRules columnarRules = ColumnarRules.builder()
            .columnsToPseudonymize(Set.of("email"))
            .build();

        MultiTypeBulkDataRules multiTypeBulkDataRules = MultiTypeBulkDataRules.builder()
            .fileRules(
                new TreeMap<>(Map.of(
                    "/export/{week}/data_{shard}.csv", columnarRules,
                    "/export/{week}/index_{shard}.ndjson", recordRules
                    )))
            .build();

        assertEquals(EXPECTED, yamlMapper.writeValueAsString(multiTypeBulkDataRules));
    }

}
