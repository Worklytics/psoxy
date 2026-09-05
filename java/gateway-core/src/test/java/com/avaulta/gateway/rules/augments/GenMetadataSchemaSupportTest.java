package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GenMetadataSchemaSupportTest {

    @Test
    void classifyShape_detectsSingleEnumProperty() {
        JsonSchemaFilter schema = categorySchema();
        Optional<GenMetadataSchemaSupport.ClassifyShape> shape =
            GenMetadataSchemaSupport.classifyShape(schema);
        assertTrue(shape.isPresent());
        assertEquals("category", shape.get().getPropertyName());
        assertEquals(GenMetadataSchemaSupport.Mode.CLASSIFY, GenMetadataSchemaSupport.mode(schema));
    }

    @Test
    void classifyShape_rejectsMultiPropertySchemas() {
        JsonSchemaFilter schema = JsonSchemaFilter.builder()
            .type("object")
            .required(List.of("speakers"))
            .properties(Map.of(
                "speakers", JsonSchemaFilter.builder()
                    .type("array")
                    .items(JsonSchemaFilter.builder().type("object").build())
                    .build()))
            .build();
        assertTrue(GenMetadataSchemaSupport.classifyShape(schema).isEmpty());
        assertEquals(GenMetadataSchemaSupport.Mode.EXTRACT, GenMetadataSchemaSupport.mode(schema));
    }

    @Test
    void wrapClassifyLabel_acceptsBareAndQuoted() {
        GenMetadataSchemaSupport.ClassifyShape shape =
            GenMetadataSchemaSupport.classifyShape(categorySchema()).orElseThrow();
        assertEquals(Map.of("category", "Excluded"),
            GenMetadataSchemaSupport.wrapClassifyLabel("Excluded", shape).orElseThrow());
        assertEquals(Map.of("category", "Excluded"),
            GenMetadataSchemaSupport.wrapClassifyLabel("\"Excluded\"", shape).orElseThrow());
        assertTrue(GenMetadataSchemaSupport.wrapClassifyLabel("{\"category\":\"Excluded\"}", shape).isEmpty());
    }

    @Test
    void processor_parsesBareClassifyLabel() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            (prompt, schema, input) -> "Research and Ideation",
            new ObjectMapper(),
            4096);
        Object out = processor.compute(
            Augment.GenMetadata.builder()
                .jsonPath("$..content")
                .prompt("classify")
                .outputSchema(categorySchema())
                .build(),
            "write an email");
        assertEquals(Map.of("category", "Research and Ideation"), out);
    }

    private static JsonSchemaFilter categorySchema() {
        return JsonSchemaFilter.builder()
            .type("object")
            .required(List.of("category"))
            .properties(Map.of(
                "category", JsonSchemaFilter.builder()
                    .type("string")
                    .enumValues(List.of(
                        "Email Drafting",
                        "Research and Ideation",
                        "Uncategorized",
                        "Excluded"))
                    .build()))
            .build();
    }
}
