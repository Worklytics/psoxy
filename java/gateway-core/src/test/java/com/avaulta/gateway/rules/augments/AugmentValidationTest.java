package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchema;
import com.avaulta.gateway.rules.RecordRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AugmentValidationTest {

    @Test
    void validateGenMetadata_requiresPromptAndSchema() {
        Augment.GenMetadata invalid = Augment.GenMetadata.builder()
            .jsonPath("$.body.content")
            .build();

        assertThrows(IllegalArgumentException.class,
            () -> AugmentValidation.validateEndpoints(List.of(
                Endpoint.builder().augment(invalid).pathTemplate("/test").build())));
    }

    @Test
    void validateGenMetadata_valid() {
        Augment.GenMetadata valid = Augment.GenMetadata.builder()
            .jsonPath("$.body.content")
            .prompt("Classify the prompt")
            .outputSchema(JsonSchema.builder()
                .type("object")
                .properties(java.util.Map.of(
                    "category", JsonSchema.builder().type("string").build()))
                .build())
            .build();

        assertDoesNotThrow(() -> AugmentValidation.validateEndpoints(List.of(
            Endpoint.builder().augment(valid).pathTemplate("/test").build())));
    }

    @Test
    void validateGenMetadata_rejectsNonPositiveMaxTokens() {
        Augment.GenMetadata invalid = Augment.GenMetadata.builder()
            .jsonPath("$.body.content")
            .prompt("Classify the prompt")
            .maxOutputTokens(0)
            .outputSchema(JsonSchema.builder()
                .type("string")
                .enumValues(List.of("Feature"))
                .build())
            .build();

        assertThrows(IllegalArgumentException.class,
            () -> AugmentValidation.validateEndpoints(List.of(
                Endpoint.builder().augment(invalid).pathTemplate("/test").build())));
    }

    @Test
    void validateGenMetadata_rejectsNonPositiveMaxInputTokens() {
        Augment.GenMetadata invalid = Augment.GenMetadata.builder()
            .jsonPath("$.body.content")
            .prompt("Classify the prompt")
            .maxInputTokens(0)
            .outputSchema(JsonSchema.builder()
                .type("string")
                .enumValues(List.of("Feature"))
                .build())
            .build();

        assertThrows(IllegalArgumentException.class,
            () -> AugmentValidation.validateEndpoints(List.of(
                Endpoint.builder().augment(invalid).pathTemplate("/test").build())));
    }

    @Test
    void validateRecordRules_genMetadataRequiresPromptAndSchema() {
        Augment.GenMetadata invalid = Augment.GenMetadata.builder()
            .jsonPath("$.content")
            .build();

        assertThrows(IllegalArgumentException.class,
            () -> AugmentValidation.validateRecordRules(
                RecordRules.builder().augment(invalid).build()));
    }
}
