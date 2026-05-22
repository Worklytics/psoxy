package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates augment rules at load time.
 */
public final class AugmentValidation {

    private AugmentValidation() {}

    public static void validateEndpoints(Iterable<Endpoint> endpoints) {
        List<String> errors = new ArrayList<>();
        if (endpoints != null) {
            for (Endpoint endpoint : endpoints) {
                if (endpoint.getAugments() != null) {
                    for (Augment augment : endpoint.getAugments()) {
                        validateAugment(augment, errors);
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                "Invalid augment configuration: " + String.join("; ", errors));
        }
    }

    static void validateAugment(Augment augment, List<String> errors) {
        if (augment instanceof Augment.GenMetadata gen) {
            if (StringUtils.isBlank(gen.getPrompt())) {
                errors.add("genMetadata augment requires non-blank prompt");
            }
            if (gen.getOutputSchema() == null) {
                errors.add("genMetadata augment requires outputSchema");
            }
            if (gen.getJsonPaths() == null || gen.getJsonPaths().isEmpty()) {
                errors.add("genMetadata augment requires at least one jsonPath");
            }
        }
    }
}
