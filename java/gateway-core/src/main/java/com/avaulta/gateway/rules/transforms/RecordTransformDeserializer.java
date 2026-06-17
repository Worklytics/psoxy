package com.avaulta.gateway.rules.transforms;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * Deserializes shorthand {@code typeName: value} entries in {@code RecordRules.transforms}.
 */
public class RecordTransformDeserializer extends JsonDeserializer<RecordTransform> {

    private static final Map<String, Class<? extends RecordTransform>> TYPES = Map.of(
        "redact", RecordTransform.Redact.class,
        "pseudonymize", RecordTransform.Pseudonymize.class,
        "tokenize", RecordTransform.Tokenize.class,
        "textDigest", RecordTransform.TextDigest.class,
        "textDigestKeywords", RecordTransform.TextDigestKeywords.class
    );

    @Override
    public RecordTransform deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode node = mapper.readTree(parser);

        if (!node.isObject() || node.size() != 1) {
            throw new IOException("Expected a single-key map for RecordTransform, got: " + node);
        }

        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        Map.Entry<String, JsonNode> entry = fields.next();
        String typeName = entry.getKey();
        Class<? extends RecordTransform> type = TYPES.get(typeName);
        if (type == null) {
            throw new IOException("Unknown RecordTransform type: " + typeName);
        }

        if (type == RecordTransform.TextDigestKeywords.class) {
            return mapper.treeToValue(entry.getValue(), type);
        }

        // Path-list transforms expect their value under a property matching the type name.
        var wrapper = mapper.createObjectNode();
        wrapper.set(typeName, entry.getValue());
        return mapper.treeToValue(wrapper, type);
    }
}
