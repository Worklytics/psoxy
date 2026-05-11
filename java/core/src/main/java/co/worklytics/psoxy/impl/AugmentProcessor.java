package co.worklytics.psoxy.impl;

import com.avaulta.gateway.rules.augments.Augment;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Applies augments to a JSON document, adding synthetic sibling properties
 * named {@code +{sourceProperty}:{augmentFunction}} alongside source fields.
 *
 * <p>Processing is intentionally non-fatal: any augment failure (exception, timeout,
 * schema validation) results in the augment property being omitted with a warning logged.
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

    @Inject
    Configuration jsonConfiguration;

    /**
     * Cache of pre-compiled JsonPaths, keyed by Augment identity.
     * Avoids recompiling paths on every request (same pattern as
     * {@code RESTApiSanitizerImpl.compiledTransforms}).
     */
    private final Map<Augment, List<JsonPath>> compiledAugmentPaths = new ConcurrentHashMap<>();

    /**
     * Apply a list of augments to a parsed JSON document.
     *
     * @param augments the augment rules to apply
     * @param document the Jayway JSONPath document (will be mutated in place)
     */
    public void applyAugments(List<Augment> augments, Object document) {
        if (augments == null || augments.isEmpty()) {
            return;
        }

        // Conflict check: if any existing property starts with "+", skip all augments
        if (hasConflictingProperties(document)) {
            log.warning("Response contains properties starting with '" + AUGMENT_PROPERTY_PREFIX
                + "'; skipping all augment processing to avoid conflicts.");
            return;
        }

        for (Augment augment : augments) {
            try {
                applyAugment(augment, document);
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Augment '" + augment.getFunctionName() + "' failed; skipping.", e);
            }
        }
    }

    /**
     * Apply a single augment to the document using its pre-compiled paths.
     */
    private void applyAugment(Augment augment, Object document) {
        List<JsonPath> paths = compiledAugmentPaths.computeIfAbsent(augment,
            a -> a.getJsonPaths().stream()
                .map(JsonPath::compile)
                .toList());

        for (JsonPath compiledPath : paths) {
            try {
                applyAugmentAtPath(augment, document, compiledPath);
            } catch (PathNotFoundException e) {
                // expected if path doesn't match this particular document — no-op
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Augment '" + augment.getFunctionName() + "' failed at path '"
                        + compiledPath.getPath() + "'; skipping.", e);
            }
        }
    }

    /**
     * Apply an augment to all values matching a single compiled JSON path.
     *
     * <p>Uses {@code AS_PATH_LIST} to resolve the path expression to concrete paths
     * (no wildcards), then derives the parent of each concrete path to insert the
     * sibling augment property.
     */
    @SuppressWarnings("unchecked")
    private void applyAugmentAtPath(Augment augment, Object document, JsonPath compiledPath) {

        // Build a configuration that returns resolved path strings instead of values
        Configuration pathListConfig = jsonConfiguration.setOptions(Option.AS_PATH_LIST);

        // Resolve to concrete paths like ["$['value'][0]['body']['content']", ...]
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
            try {
                applyAugmentAtConcretePath(augment, document, concretePath);
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Augment '" + augment.getFunctionName() + "' failed at concrete path '"
                        + concretePath + "'; skipping.", e);
            }
        }
    }

    /**
     * Apply an augment at a single concrete (fully-resolved) JSON path.
     *
     * <p>Reads the source value, computes the augment, and inserts the result as a
     * sibling property in the parent object.
     */
    @SuppressWarnings("unchecked")
    private void applyAugmentAtConcretePath(Augment augment, Object document, String concretePath) {
        // Derive the leaf field name and parent path from the concrete path
        String leafFieldName = extractLeafFieldNameFromConcrete(concretePath);
        String parentPath = extractParentFromConcrete(concretePath);

        if (parentPath == null || leafFieldName == null) {
            log.warning("Cannot derive parent for concrete path: " + concretePath);
            return;
        }

        // Read the parent object
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

        // Read the source value
        Object sourceValue;
        try {
            sourceValue = JsonPath.compile(concretePath).read(document, jsonConfiguration);
        } catch (PathNotFoundException e) {
            return;
        }

        // Compute and insert
        String augmentPropertyName = AUGMENT_PROPERTY_PREFIX + leafFieldName
            + AUGMENT_SEPARATOR + augment.getFunctionName();

        try {
            Object augmentValue = null;
            if (augment.getInnerJsonPath() != null && !augment.getInnerJsonPath().isEmpty() && sourceValue instanceof String jsonStr && !jsonStr.isEmpty()) {
                Object parsed = JsonPath.parse(jsonStr).read(augment.getInnerJsonPath());
                if (parsed instanceof List<?> list) {
                    List<Object> results = new java.util.ArrayList<>();
                    for (Object item : list) {
                        Object computed = augment.compute(item);
                        if (computed != null) {
                            results.add(computed);
                        }
                    }
                    if (!results.isEmpty()) {
                        augmentValue = results;
                    }
                } else if (parsed != null) {
                    augmentValue = augment.compute(parsed);
                }
            } else {
                augmentValue = augment.compute(sourceValue);
            }

            if (augmentValue == null) {
                return;
            }

            // TODO: validate against outputSchema if present (predicate check)
            // For PoC, outputSchema validation is deferred

            ((Map<String, Object>) parent).put(augmentPropertyName, augmentValue);
        } catch (Exception e) {
            log.log(Level.WARNING,
                "Augment '" + augment.getFunctionName() + "' compute failed for property '"
                    + augmentPropertyName + "'; omitting.", e);
        }
    }

    /**
     * Check if the document contains any properties starting with the augment prefix.
     * Scans the top-level and one level of nesting (covers typical API response shapes).
     */
    boolean hasConflictingProperties(Object document) {
        if (document instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                if (key instanceof String s && s.startsWith(AUGMENT_PROPERTY_PREFIX)) {
                    return true;
                }
                // Check one level deeper (e.g., $.value[*] arrays contain objects)
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

    /**
     * Extract the leaf field name from a concrete (resolved) JSON path.
     * Concrete paths use bracket notation: {@code $['body']['content']} → {@code "content"}.
     */
    static String extractLeafFieldNameFromConcrete(String concretePath) {
        // Concrete paths from Jayway use bracket notation: $['body']['content']
        // Find the last ['...'] segment
        int lastBracket = concretePath.lastIndexOf("['");
        if (lastBracket >= 0) {
            int end = concretePath.indexOf("']", lastBracket);
            if (end > lastBracket + 2) {
                return concretePath.substring(lastBracket + 2, end);
            }
        }
        return null;
    }

    /**
     * Extract the parent path from a concrete (resolved) JSON path.
     * {@code $['body']['content']} → {@code $['body']}.
     */
    static String extractParentFromConcrete(String concretePath) {
        int lastBracket = concretePath.lastIndexOf("['");
        if (lastBracket > 0) {
            return concretePath.substring(0, lastBracket);
        }
        // If no bracket found, try numeric index: $['value'][0]
        int lastNumBracket = concretePath.lastIndexOf("[");
        if (lastNumBracket > 0) {
            return concretePath.substring(0, lastNumBracket);
        }
        return null;
    }

    // keep legacy helpers for tests that use them directly
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
