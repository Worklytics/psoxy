
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
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor // for builder
@Data
@JsonPropertyOrder({"type", "format", "items", "properties", "required", "enumValues",
        "additionalProperties"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonSchema {


    // NOTE: $schema omitted; presumed to be the latest 2020-12 draft, eg
    // "https://json-schema.org/draft/2020-12/schema"

    /**
     * type of value defined by this schema; one of "string", "number", "integer", "boolean",
     * "object", "array", ...
     */
    String type;

    /**
     * format for string validation; only applicable if type==string
     * 
     */
    String format;

    /**
     * schema for properties in the object, by property name; only applicable if type==object
     */
    Map<String, JsonSchema> properties;

    /**
     * schema for items in the array; only applicable if type==array
     */
    JsonSchema items;

    /**
     * list of required properties by name, if any (only for objects)
     * 
     * properties are OPTIONAL by default, unless included in this list.
     */
    List<String> required;

    /**
     * pattern for string validation; only applicable if type==string
     */
    String pattern;

    /**
     * enum values for string validation; NOT applicable if type==object or type==array
     */
    List<String> enumValues;

    /**
     * whether additional properties are allowed for this object; only applicable if type==object;
     * default is true
     */
    Boolean additionalProperties;

    /**
     * Custom string formats; only applicable if type==string
     */
    public enum StringFormat {

        /**
         * a pseudonym, as defined by the pseudonym encoder, which the proxy can reverse back to
         * original value before sending to the source API.
         */
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
