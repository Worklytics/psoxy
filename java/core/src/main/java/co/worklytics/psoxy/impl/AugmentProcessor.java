package co.worklytics.psoxy.impl;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import co.worklytics.psoxy.Warning;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Applies augments to a JSON document, adding synthetic sibling properties
 * named {@code +{sourceProperty}:{augmentFunction}} alongside source fields.
 *
 * <p>Processing is intentionally non-fatal: {@link AugmentProcessingException} and other failures
 * omit the augment property and add {@code X-Psoxy-Warning} codes via the returned list.
 *
 * <p>NOTE: this class must be thread-safe. A single instance may be shared across
 * concurrent requests. Compiled JsonPaths are cached in a ConcurrentHashMap.
 *
 * @see <a href="file:///docs/development/augments.md">Augments Design Doc</a>
 */
@Log
public class AugmentProcessor {

    /** Prefix for augment-generated properties. */
    public static final String AUGMENT_PROPERTY_PREFIX = "+";

    /** Separator between source property name and augment function name. */
    public static final String AUGMENT_SEPARATOR = ":";

    final Configuration jsonConfiguration;
    final JsonSchemaValidationUtils jsonSchemaValidationUtils;
    final ObjectMapper objectMapper;

    @Inject
    public AugmentProcessor(Configuration jsonConfiguration,
                            JsonSchemaValidationUtils jsonSchemaValidationUtils,
                            ObjectMapper objectMapper) {
        this.jsonConfiguration = jsonConfiguration;
        this.jsonSchemaValidationUtils = jsonSchemaValidationUtils;
        this.objectMapper = objectMapper;
    }

    private final Map<Augment, List<JsonPath>> compiledAugmentPaths = new ConcurrentHashMap<>();

    /**
     * @return warning header codes to add to the response (may be empty)
     */
    public List<String> applyAugments(List<Augment> augments, Object document) {
        if (augments == null || augments.isEmpty()) {
            return List.of();
        }

        List<String> warnings = new ArrayList<>();

        if (hasConflictingProperties(document)) {
            log.warning("Response contains properties starting with '" + AUGMENT_PROPERTY_PREFIX
                + "'; skipping all augment processing to avoid conflicts.");
            warnings.add(Warning.AUGMENT_CONFLICT_SKIPPED.asHttpHeaderCode());
            return List.copyOf(warnings);
        }

        for (Augment augment : augments) {
            try {
                applyAugment(augment, document);
            } catch (AugmentProcessingException e) {
                log.log(Level.WARNING, e.getMessage(), e);
                warnings.add(e.getWarningCode());
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Augment '" + augment.getFunctionName() + "' failed; skipping.", e);
            }
        }
        return List.copyOf(warnings);
    }

    private void applyAugment(Augment augment, Object document) throws AugmentProcessingException {
        List<JsonPath> paths = compiledAugmentPaths.computeIfAbsent(augment,
            a -> a.getJsonPaths().stream()
                .map(JsonPath::compile)
                .toList());

        for (JsonPath compiledPath : paths) {
            try {
                applyAugmentAtPath(augment, document, compiledPath);
            } catch (PathNotFoundException e) {
                // expected if path doesn't match this particular document — no-op
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyAugmentAtPath(Augment augment, Object document, JsonPath compiledPath)
            throws AugmentProcessingException {

        Configuration pathListConfig = jsonConfiguration.setOptions(Option.AS_PATH_LIST);

        List<String> resolvedPaths;
        try {
            resolvedPaths = compiledPath.read(document, pathListConfig);
        } catch (PathNotFoundException e) {
            return;
        }

        if (resolvedPaths == null || resolvedPaths.isEmpty()) {
            return;
        }

        for (String concretePath : resolvedPaths) {
            applyAugmentAtConcretePath(augment, document, concretePath);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyAugmentAtConcretePath(Augment augment, Object document, String concretePath)
            throws AugmentProcessingException {

        String leafFieldName = extractLeafFieldNameFromConcrete(concretePath);
        String parentPath = extractParentFromConcrete(concretePath);

        if (parentPath == null || leafFieldName == null) {
            log.warning("Cannot derive parent for concrete path: " + concretePath);
            return;
        }

        Object parent;
        try {
            parent = JsonPath.compile(parentPath).read(document, jsonConfiguration);
        } catch (PathNotFoundException e) {
            log.warning("Parent path '" + parentPath + "' not found for concrete path '"
                + concretePath + "'; this should not happen — indicates a bug.");
            return;
        }

        if (!(parent instanceof Map<?, ?>)) {
            log.warning("Parent at '" + parentPath + "' is not a Map (type: "
                + (parent == null ? "null" : parent.getClass().getSimpleName())
                + "); cannot insert augment sibling property.");
            return;
        }

        Object sourceValue;
        try {
            sourceValue = JsonPath.compile(concretePath).read(document, jsonConfiguration);
        } catch (PathNotFoundException e) {
            return;
        }

        String augmentPropertyName = AUGMENT_PROPERTY_PREFIX + leafFieldName
            + AUGMENT_SEPARATOR + augment.getFunctionName();

        Object augmentValue = computeAugmentValue(augment, sourceValue);

        if (augmentValue == null) {
            if (augment instanceof Augment.GenMetadata) {
                throw new AugmentProcessingException(Warning.AUGMENT_GEN_UNAVAILABLE,
                    "genMetadata returned no value for property '" + augmentPropertyName + "'");
            }
            return;
        }

        if (!validateOutputSchema(augment, augmentValue)) {
            throw new AugmentProcessingException(Warning.AUGMENT_OUTPUT_SCHEMA_MISMATCH,
                "Augment '" + augment.getFunctionName()
                    + "' output failed schema validation for property '" + augmentPropertyName
                    + "'");
        }

        ((Map<String, Object>) parent).put(augmentPropertyName, augmentValue);
    }

    private Object computeAugmentValue(Augment augment, Object sourceValue)
            throws AugmentProcessingException {
        if (augment.getInnerJsonPath() != null && !augment.getInnerJsonPath().isEmpty()
                && sourceValue instanceof String jsonStr && !jsonStr.isEmpty()) {
            Object parsed = JsonPath.parse(jsonStr).read(augment.getInnerJsonPath());
            if (parsed instanceof List<?> list) {
                List<Object> results = new ArrayList<>();
                for (Object item : list) {
                    Object computed = invokeCompute(augment, item);
                    if (computed != null) {
                        results.add(computed);
                    }
                }
                return results.isEmpty() ? null : results;
            }
            return parsed != null ? invokeCompute(augment, parsed) : null;
        }
        return invokeCompute(augment, sourceValue);
    }

    private Object invokeCompute(Augment augment, Object input) throws AugmentProcessingException {
        try {
            return augment.compute(input);
        } catch (GenMetadataAugmentException e) {
            throw toAugmentProcessingException(e);
        }
    }

    private static AugmentProcessingException toAugmentProcessingException(
            GenMetadataAugmentException e) {
        Warning warning = switch (e.getCode()) {
            case UNAVAILABLE -> Warning.AUGMENT_GEN_UNAVAILABLE;
            case INFERENCE_FAILED -> Warning.AUGMENT_GEN_INFERENCE_FAILED;
        };
        return e.getCause() != null
            ? new AugmentProcessingException(warning, e.getMessage(), e.getCause())
            : new AugmentProcessingException(warning, e.getMessage());
    }

    private boolean validateOutputSchema(Augment augment, Object augmentValue) {
        JsonSchemaFilter outputSchema = augment.getOutputSchema();
        if (outputSchema == null) {
            return true;
        }
        try {
            String json = objectMapper.writeValueAsString(augmentValue);
            return jsonSchemaValidationUtils.validateJsonBySchema(json, outputSchema);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to validate augment output schema", e);
            return false;
        }
    }

    boolean hasConflictingProperties(Object document) {
        if (document instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                if (key instanceof String s && s.startsWith(AUGMENT_PROPERTY_PREFIX)) {
                    return true;
                }
                Object val = map.get(key);
                if (val instanceof Map<?, ?> nested) {
                    if (hasAugmentPrefixKeys(nested)) {
                        return true;
                    }
                } else if (val instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> nested && hasAugmentPrefixKeys(nested)) {
                            return true;
                        }
                    }
                }
            }
        } else if (document instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && hasAugmentPrefixKeys(map)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAugmentPrefixKeys(Map<?, ?> map) {
        for (Object key : map.keySet()) {
            if (key instanceof String s && s.startsWith(AUGMENT_PROPERTY_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    static String extractLeafFieldNameFromConcrete(String concretePath) {
        int lastBracket = concretePath.lastIndexOf("['");
        if (lastBracket >= 0) {
            int end = concretePath.indexOf("']", lastBracket);
            if (end > lastBracket + 2) {
                return concretePath.substring(lastBracket + 2, end);
            }
        }
        return null;
    }

    static String extractParentFromConcrete(String concretePath) {
        int lastBracket = concretePath.lastIndexOf("['");
        if (lastBracket > 0) {
            return concretePath.substring(0, lastBracket);
        }
        int lastNumBracket = concretePath.lastIndexOf("[");
        if (lastNumBracket > 0) {
            return concretePath.substring(0, lastNumBracket);
        }
        return null;
    }

    static String extractLeafFieldName(String jsonPath) {
        String cleaned = jsonPath.replaceAll("\\[\\*]$", "");
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < cleaned.length() - 1) {
            return cleaned.substring(lastDot + 1);
        }
        return cleaned;
    }

    static String extractParentPath(String jsonPath) {
        String cleaned = jsonPath.replaceAll("\\[\\*]$", "");
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot > 0) {
            return cleaned.substring(0, lastDot);
        }
        return null;
    }
}
