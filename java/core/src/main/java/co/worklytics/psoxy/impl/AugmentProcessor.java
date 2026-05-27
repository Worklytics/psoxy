package co.worklytics.psoxy.impl;

import com.avaulta.gateway.rules.augments.Augment;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
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
 * named {@code +{sourceProperty}:{augmentFunction}}. When {@code innerJsonPath} is used, each
 * inner match is augmented in place within the parsed embedded JSON, then the modified structure
 * is stored as the augment property value (mirroring {@code Transform.TextDigest} with
 * {@code isJsonEscaped}).
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

    /** Default config: reads return matched values (used for parent/source lookups). */
    final Configuration jsonConfiguration;

    /**
     * Same underlying provider as {@link #jsonConfiguration}, but with {@link Option#AS_PATH_LIST}:
     * reads return concrete path strings (e.g. {@code $['value'][0]['body']['content']}) instead
     * of values. Needed to expand wildcard rule paths like {@code $..attachments[*].content} into
     * per-match locations where we insert sibling {@code +field:augment} properties.
     * Cached here to avoid rebuilding on every augment-path evaluation.
     */
    final Configuration pathListConfiguration;

    @Inject
    public AugmentProcessor(Configuration jsonConfiguration) {
        this.jsonConfiguration = jsonConfiguration;
        this.pathListConfiguration = jsonConfiguration.setOptions(Option.AS_PATH_LIST);
    }

    /**
     * Cache of pre-compiled JsonPaths, keyed by Augment identity.
     * Avoids recompiling paths on every request (same pattern as
     * {@code RESTApiSanitizerImpl.compiledTransforms}).
     */
    private final Map<Augment, List<JsonPath>> compiledAugmentPaths = new ConcurrentHashMap<>();

    /**
     * Cache of pre-compiled concrete/parent JsonPaths resolved at runtime.
     */
    private final Map<String, JsonPath> compiledPathCache = new ConcurrentHashMap<>();

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
        List<String> resolvedPaths;
        try {
            resolvedPaths = compiledPath.read(document, pathListConfiguration);
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
        String leafFieldName = extractLeafFieldNameFromConcrete(concretePath);
        String parentPath = extractParentFromConcrete(concretePath);

        if (parentPath == null || leafFieldName == null) {
            log.warning("Cannot derive parent for concrete path: " + concretePath);
            return;
        }

        Object parent;
        try {
            parent = getCompiledPath(parentPath).read(document, jsonConfiguration);
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
            sourceValue = getCompiledPath(concretePath).read(document, jsonConfiguration);
        } catch (PathNotFoundException e) {
            return;
        }

        String augmentPropertyName = buildAugmentPropertyName(leafFieldName, augment.getFunctionName());

        try {
            if (hasInnerJsonPath(augment) && sourceValue instanceof String jsonStr && !jsonStr.isEmpty()) {
                applyInnerJsonPathAugments(augment, (Map<String, Object>) parent, leafFieldName, jsonStr);
                return;
            }

            Object augmentValue = augment.compute(sourceValue);
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

    private boolean hasInnerJsonPath(Augment augment) {
        return augment.getInnerJsonPath() != null && !augment.getInnerJsonPath().isEmpty();
    }

    /**
     * When {@link Augment#getInnerJsonPath()} is set, resolve each concrete inner path, replace
 * matched inner field values with serialized augment output, then store the modified embedded
 * JSON re-serialized as a string under {@code +{outerLeaf}:{fn}} (mirroring
 * {@code Transform.TextDigest} with {@code isJsonEscaped}).
     */
    private void applyInnerJsonPathAugments(Augment augment, Map<String, Object> parent,
                                            String leafFieldName, String jsonStr) {
        DocumentContext innerContext = JsonPath.parse(jsonStr);
        Object innerDocument = innerContext.json();

        List<String> innerConcretePaths;
        try {
            innerConcretePaths = getCompiledPath(augment.getInnerJsonPath())
                .read(innerDocument, pathListConfiguration);
        } catch (PathNotFoundException e) {
            return;
        }
        if (innerConcretePaths == null || innerConcretePaths.isEmpty()) {
            return;
        }

        boolean anyApplied = false;

        for (String innerConcretePath : innerConcretePaths) {
            try {
                Object innerValue = innerContext.read(innerConcretePath);
                Object augmentValue = augment.compute(innerValue);
                if (augmentValue == null) {
                    continue;
                }

                // TODO: validate against outputSchema if present (predicate check)
                String serializedAugment = jsonConfiguration.jsonProvider().toJson(augmentValue);
                innerContext.set(innerConcretePath, serializedAugment);
                anyApplied = true;
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Augment '" + augment.getFunctionName() + "' failed at inner path '"
                        + innerConcretePath + "'; skipping.", e);
            }
        }

        if (anyApplied) {
            parent.put(buildAugmentPropertyName(leafFieldName, augment.getFunctionName()),
                innerContext.jsonString());
        }
    }

    /**
     * Build augment property name: {@code +content:textDigest}.
     */
    static String buildAugmentPropertyName(String leafFieldName, String functionName) {
        return AUGMENT_PROPERTY_PREFIX + leafFieldName + AUGMENT_SEPARATOR + functionName;
    }

    /**
     * Normalize a Jayway concrete inner path to a dot/bracket suffix.
     * {@code $['body'][0]['text']} → {@code body[0].text}.
     */
    static String toInnerPathSuffix(String concreteInnerPath) {
        if (concreteInnerPath == null || concreteInnerPath.isEmpty()) {
            return "";
        }
        String path = concreteInnerPath.startsWith("$")
            ? concreteInnerPath.substring(1)
            : concreteInnerPath;
        path = path.replaceAll("\\['([^']+)'\\]", ".$1");
        if (path.startsWith(".")) {
            path = path.substring(1);
        }
        return path;
    }

    private JsonPath getCompiledPath(String path) {
        return compiledPathCache.computeIfAbsent(path, JsonPath::compile);
    }

    /**
     * Check if the document contains any properties starting with the augment prefix.
     */
    boolean hasConflictingProperties(Object document) {
        if (document instanceof Map<?, ?> map) {
            return hasConflictingPropertiesInMap(map);
        } else if (document instanceof List<?> list) {
            for (Object item : list) {
                if (hasConflictingProperties(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasConflictingPropertiesInMap(Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String s && s.startsWith(AUGMENT_PROPERTY_PREFIX)) {
                return true;
            }
            if (hasConflictingProperties(entry.getValue())) {
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
