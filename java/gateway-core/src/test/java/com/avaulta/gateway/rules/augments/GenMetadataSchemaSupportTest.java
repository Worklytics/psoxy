package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchema;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GenMetadataSchemaSupportTest {

    @Test
    void classifyShape_detectsSingleEnumProperty() {
        JsonSchema schema = categorySchema();
        Optional<GenMetadataSchemaSupport.ClassifyShape> shape =
            GenMetadataSchemaSupport.classifyShape(schema);
        assertTrue(shape.isPresent());
        assertEquals("category", shape.get().getPropertyName());
        assertEquals(GenMetadataSchemaSupport.Mode.CLASSIFY, GenMetadataSchemaSupport.mode(schema));
    }

    @Test
    void classifyShape_detectsRootStringEnum() {
        JsonSchema schema = stringEnumSchema();
        Optional<GenMetadataSchemaSupport.ClassifyShape> shape =
            GenMetadataSchemaSupport.classifyShape(schema);
        assertTrue(shape.isPresent());
        assertTrue(shape.get().isRootString());
        assertEquals(GenMetadataSchemaSupport.Mode.CLASSIFY, GenMetadataSchemaSupport.mode(schema));
    }

    @Test
    void classifyShape_rejectsMultiPropertySchemas() {
        JsonSchema schema = JsonSchema.builder()
            .type("object")
            .required(List.of("speakers"))
            .properties(Map.of(
                "speakers", JsonSchema.builder()
                    .type("array")
                    .items(JsonSchema.builder().type("object").build())
                    .build()))
            .build();
        assertTrue(GenMetadataSchemaSupport.classifyShape(schema).isEmpty());
        assertEquals(GenMetadataSchemaSupport.Mode.COMPUTE, GenMetadataSchemaSupport.mode(schema));
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
    void wrapClassifyLabel_recoversEnumFromProse() {
        GenMetadataSchemaSupport.ClassifyShape shape =
            GenMetadataSchemaSupport.classifyShape(categorySchema()).orElseThrow();
        assertEquals(Map.of("category", "Email Drafting"),
            GenMetadataSchemaSupport.wrapClassifyLabel(
                "Here is the label: Email Drafting", shape).orElseThrow());
        assertEquals(Map.of("category", "Research and Ideation"),
            GenMetadataSchemaSupport.findEnumInText(
                "{\"category\": \"Research and Ideation", shape).orElseThrow());
    }

    @Test
    void processor_parsesBareClassifyLabel() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            (prompt, schema, input) -> "Research and Ideation",
            new ObjectMapper(), 2, new JsonSchemaValidationUtils());
        Object out = processor.compute(
            Augment.GenMetadata.builder()
                .jsonPath("$..content")
                .prompt("classify")
                .outputSchema(categorySchema())
                .build(),
            "write an email");
        assertEquals(Map.of("category", "Research and Ideation"), out);
    }

    @Test
    void processor_parsesJsonStringForRootStringEnum() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            (prompt, schema, input) -> "\"Research and Ideation\"",
            new ObjectMapper(), 2, new JsonSchemaValidationUtils());
        Object out = processor.compute(
            Augment.GenMetadata.builder()
                .jsonPath("$..content")
                .prompt("classify")
                .outputSchema(stringEnumSchema())
                .build(),
            "write an email");
        assertEquals("Research and Ideation", out);
    }

    @Test
    void processor_parsesBareLabelForRootStringEnum() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            (prompt, schema, input) -> "Excluded",
            new ObjectMapper(), 2, new JsonSchemaValidationUtils());
        Object out = processor.compute(
            Augment.GenMetadata.builder()
                .jsonPath("$..content")
                .prompt("classify")
                .outputSchema(stringEnumSchema())
                .build(),
            "thanks");
        assertEquals("Excluded", out);
    }

    private static JsonSchema stringEnumSchema() {
        return JsonSchema.builder()
            .type("string")
            .enumValues(List.of(
                "Email Drafting",
                "Research and Ideation",
                "Uncategorized",
                "Excluded"))
            .build();
    }

    private static JsonSchema categorySchema() {
        return JsonSchema.builder()
            .type("object")
            .required(List.of("category"))
            .properties(Map.of(
                "category", JsonSchema.builder()
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
