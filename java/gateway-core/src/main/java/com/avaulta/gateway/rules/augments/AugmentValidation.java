package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.RecordRules;
import com.avaulta.gateway.rules.WebhookCollectionRules;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates augment rules at load time.
 */
public final class AugmentValidation {

    private AugmentValidation() {}

    public static void validateAugments(Iterable<Augment> augments) {
        List<String> errors = new ArrayList<>();
        collectAugmentErrors(augments, errors);
        throwIfErrors(errors);
    }

    public static void validateEndpoints(Iterable<Endpoint> endpoints) {
        List<String> errors = new ArrayList<>();
        if (endpoints != null) {
            for (Endpoint endpoint : endpoints) {
                collectAugmentErrors(endpoint.getAugments(), errors);
            }
        }
        throwIfErrors(errors);
    }

    public static void validateRecordRules(RecordRules rules) {
        if (rules != null) {
            validateAugments(rules.getAugments());
        }
    }

    public static void validateWebhookEndpoints(
            Iterable<WebhookCollectionRules.WebhookEndpoint> endpoints) {
        List<String> errors = new ArrayList<>();
        if (endpoints != null) {
            for (WebhookCollectionRules.WebhookEndpoint endpoint : endpoints) {
                collectAugmentErrors(endpoint.getAugments(), errors);
            }
        }
        throwIfErrors(errors);
    }

    private static void collectAugmentErrors(Iterable<Augment> augments, List<String> errors) {
        if (augments != null) {
            for (Augment augment : augments) {
                validateAugment(augment, errors);
            }
        }
    }

    private static void throwIfErrors(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                "Invalid augment configuration: " + String.join("; ", errors));
        }
    }

    static void validateAugment(Augment augment, List<String> errors) {
        if (augment instanceof Augment.Classify classify) {
            if (StringUtils.isBlank(classify.getPrompt())) {
                errors.add("classify augment requires non-blank prompt");
            }
            if (classify.getClasses() == null || classify.getClasses().isEmpty()) {
                errors.add("classify augment requires at least one class");
            } else {
                boolean anyNonBlank = false;
                for (String value : classify.getClasses()) {
                    if (StringUtils.isNotBlank(value)) {
                        anyNonBlank = true;
                        break;
                    }
                }
                if (!anyNonBlank) {
                    errors.add("classify augment requires at least one non-blank class");
                }
            }
            if (classify.getJsonPaths() == null || classify.getJsonPaths().isEmpty()) {
                errors.add("classify augment requires at least one jsonPath");
            }
            try {
                classify.getMaxInputTokens();
            } catch (IllegalArgumentException e) {
                errors.add("classify maxInputTokens must be a positive integer when set");
            }
        }
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
            try {
                gen.getMaxOutputTokens();
            } catch (IllegalArgumentException e) {
                errors.add("genMetadata maxOutputTokens must be a positive integer when set");
            }
            try {
                gen.getMaxInputTokens();
            } catch (IllegalArgumentException e) {
                errors.add("genMetadata maxInputTokens must be a positive integer when set");
            }
        }
    }
}
