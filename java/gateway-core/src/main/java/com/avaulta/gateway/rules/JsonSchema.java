
package com.avaulta.gateway.rules;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * a JsonSchema container class, meant for serializing/deserializing JsonSchemas
 * 
 * should be mappable to/from networknt's JsonSchema
 * 
 * This is a simplified implementation that supports basic object validation
 * without deep nesting or complex JSON Schema features.
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor // for builder
@Data
@JsonPropertyOrder({"type", "format", "items", "properties", "required", "enumValues",
        "additionalProperties"})
@JsonInclude(JsonInclude.Include.NON_NULL)

public class JsonSchema {

    String type;

    // only applicable if type==String
    String format;

    Map<String, JsonSchema> properties;

    // only applicable if type==array
    JsonSchema items;

    // List of required property names (only for objects)
    List<String> required;

    // Pattern for string validation
    String pattern;

    // Enum values for string validation
    List<String> enumValues;

    // Whether additional properties are allowed (only for objects); default is true, consistent
    // with JsonSchema standard
    Boolean additionalProperties;

    public enum StringFormat {
        PSEUDONYM,
        ;

        public String getStringEncoding() {
            return this.name().replace("_", "-").toLowerCase();
        }

        static StringFormat parse(String encoded) {
            return StringFormat.valueOf(encoded.replace("-", "_").toUpperCase());
        }
    }
}
