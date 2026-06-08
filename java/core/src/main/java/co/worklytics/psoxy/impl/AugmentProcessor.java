package co.worklytics.psoxy.impl;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
import com.avaulta.gateway.rules.augments.GenMetadataProcessor;
import com.avaulta.gateway.rules.augments.SentenceMetadataProcessor;
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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Applies augments to a JSON document, adding synthetic sibling properties
 * named {@code +{sourceProperty}:{augmentFunction}}. When {@code innerJsonPath} is used, multiple
 * inner matches are grouped under that property as an object keyed by inner path suffix.
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

    /** Default config: reads return matched values (used for parent/source lookups). */
    final Configuration jsonConfiguration;
    final JsonSchemaValidationUtils jsonSchemaValidationUtils;
    final ObjectMapper objectMapper;

    /**
     * Same underlying provider as {@link #jsonConfiguration}, but with {@link Option#AS_PATH_LIST}:
     * reads return concrete path strings (e.g. {@code $['value'][0]['body']['content']}) instead
     * of values. Needed to expand wildcard rule paths like {@code $..attachments[*].content} into
     * per-match locations where we insert sibling {@code +field:augment} properties.
     * Cached here to avoid rebuilding on every augment-path evaluation.
     */
    final Configuration pathListConfiguration;

    final SentenceMetadataProcessor sentenceMetadataProcessor;
    final GenMetadataProcessor genMetadataProcessor;

    @Inject
    public AugmentProcessor(Configuration jsonConfiguration,
                            JsonSchemaValidationUtils jsonSchemaValidationUtils,
                            ObjectMapper objectMapper,
                            SentenceMetadataProcessor sentenceMetadataProcessor,
                            GenMetadataProcessor genMetadataProcessor) {
        this.jsonConfiguration = jsonConfiguration;
        this.jsonSchemaValidationUtils = jsonSchemaValidationUtils;
        this.objectMapper = objectMapper;
        this.pathListConfiguration = jsonConfiguration.setOptions(Option.AS_PATH_LIST);
        this.sentenceMetadataProcessor = sentenceMetadataProcessor;
        this.genMetadataProcessor = genMetadataProcessor;
    }

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
                warnings.addAll(applyAugment(augment, document));
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Augment '" + augment.getFunctionName() + "' failed; skipping.", e);
            }
        }
        return List.copyOf(warnings);
    }

    private List<String> applyAugment(Augment augment, Object document) {
        List<String> warnings = new ArrayList<>();
        List<JsonPath> paths = compiledAugmentPaths.computeIfAbsent(augment,
            a -> a.getJsonPaths().stream()
                .map(JsonPath::compile)
                .toList());

        for (JsonPath compiledPath : paths) {
            try {
                warnings.addAll(applyAugmentAtPath(augment, document, compiledPath));
            } catch (PathNotFoundException e) {
                // expected if path doesn't match this particular document — no-op
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Augment '" + augment.getFunctionName() + "' failed at path '"
                        + compiledPath.getPath() + "'; skipping.", e);
            }
        }
        return warnings;
    }

    @SuppressWarnings("unchecked")
    private List<String> applyAugmentAtPath(Augment augment, Object document, JsonPath compiledPath) {
        List<String> warnings = new ArrayList<>();
        List<String> resolvedPaths;
        try {
            resolvedPaths = compiledPath.read(document, pathListConfiguration);
        } catch (PathNotFoundException e) {
            return warnings;
        }

        if (resolvedPaths == null || resolvedPaths.isEmpty()) {
            return warnings;
        }

        for (String concretePath : resolvedPaths) {
            try {
                applyAugmentAtConcretePath(augment, document, concretePath);
            } catch (AugmentProcessingException e) {
                log.log(Level.WARNING, e.getMessage(), e);
                warnings.add(e.getWarningCode());
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Augment '" + augment.getFunctionName() + "' failed at concrete path '"
                        + concretePath + "'; skipping.", e);
            }
        }
        return warnings;
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

        if (hasInnerJsonPath(augment) && sourceValue instanceof String jsonStr && !jsonStr.isEmpty()) {
            applyInnerJsonPathAugments(augment, (Map<String, Object>) parent, leafFieldName, jsonStr);
            return;
        }

        Object augmentValue = invokeCompute(augment, sourceValue);

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

    private Object invokeCompute(Augment augment, Object input) throws AugmentProcessingException {
        try {
            if (augment instanceof Augment.SentenceMetadata sentenceMetadata) {
                return sentenceMetadataProcessor.compute(sentenceMetadata, input);
            }
            if (augment instanceof Augment.GenMetadata genMetadata) {
                return genMetadataProcessor.compute(genMetadata, input);
            }
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

    private boolean hasInnerJsonPath(Augment augment) {
        return augment.getInnerJsonPath() != null && !augment.getInnerJsonPath().isEmpty();
    }

    /**
     * When {@link Augment#getInnerJsonPath()} is set, resolve each concrete inner path and group
     * results under {@code +{outerLeaf}:{fn}} keyed by inner path suffix.
     */
    @SuppressWarnings("unchecked")
    private void applyInnerJsonPathAugments(Augment augment, Map<String, Object> parent,
                                            String leafFieldName, String jsonStr)
            throws AugmentProcessingException {
        JsonPath innerCompiled = getCompiledPath(augment.getInnerJsonPath());
        Object innerDocument = JsonPath.parse(jsonStr).json();

        List<String> innerConcretePaths;
        try {
            innerConcretePaths = innerCompiled.read(innerDocument, pathListConfiguration);
        } catch (PathNotFoundException e) {
            return;
        }
        if (innerConcretePaths == null || innerConcretePaths.isEmpty()) {
            return;
        }

        Map<String, Object> innerAugments = new TreeMap<>();

        for (String innerConcretePath : innerConcretePaths) {
            try {
                Object innerValue = getCompiledPath(innerConcretePath).read(innerDocument, jsonConfiguration);
                Object augmentValue = invokeCompute(augment, innerValue);
                if (augmentValue == null) {
                    continue;
                }
                if (!validateOutputSchema(augment, augmentValue)) {
                    log.warning("Augment '" + augment.getFunctionName()
                        + "' output failed schema validation at inner path '" + innerConcretePath
                        + "'; skipping.");
                    continue;
                }

                String innerKey = toInnerPathSuffix(innerConcretePath);
                innerAugments.put(innerKey, augmentValue);
            } catch (AugmentProcessingException e) {
                log.log(Level.WARNING, e.getMessage(), e);
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Augment '" + augment.getFunctionName() + "' failed at inner path '"
                        + innerConcretePath + "'; skipping.", e);
            }
        }

        if (!innerAugments.isEmpty()) {
            parent.put(buildAugmentPropertyName(leafFieldName, augment.getFunctionName()), innerAugments);
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
