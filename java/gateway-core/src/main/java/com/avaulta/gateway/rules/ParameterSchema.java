package com.avaulta.gateway.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * schema for validating a parameter value
 * <p>
 * this is just subset of JsonSchema; nothing makes it specific to a 'parameter' per se
 */
@JsonPropertyOrder({"type", "format", "pattern", "enum"})
@SuperBuilder(toBuilder = true)
@With
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
public class ParameterSchema {


    public enum ValueType {
        STRING,
        INTEGER,
        NUMBER;

        public String getEncoding() {
            return this.name().toLowerCase();
        }


        static ValueType parse(String encoded) {
            return ValueType.valueOf(encoded.toUpperCase());
        }
    }

    public enum StringFormat {
        REVERSIBLE_PSEUDONYM,
        PSEUDONYM, // alias to REVERSIBLE_PSEUDONYM
        ;

        public String getStringEncoding() {
            return this.name().replace("_", "-").toLowerCase();
        }

        static StringFormat parse(String encoded) {
            return StringFormat.valueOf(encoded.replace("-", "_").toUpperCase());
        }
    }

    /**
     * @see ValueType for possible values
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String type;

    /**
     * format for string values
     *
     * if provided and matches one of StringFormat, should validate that confirms.
     * if does not match, can be ignored.
     *
     * @see StringFormat for expected values
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String format;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String pattern;

    /**
     * value must be one of these
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("enum") //align to JsonSchema
    List<String> enumValues;

    /**
     * values matching any schema in this list are valid
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("or") //align to JsonSchema
    @Singular
    List<ParameterSchema> ors;




    public static ParameterSchema string() {
        return ParameterSchema.builder()
                .type(ValueType.STRING.getEncoding())
                .build();
    }

    public static ParameterSchema reversiblePseudonym() {
        return ParameterSchema.builder()
                .type(ValueType.STRING.getEncoding())
                .format(StringFormat.REVERSIBLE_PSEUDONYM.getStringEncoding())
                .build();
    }

    public static ParameterSchema integer() {
        return ParameterSchema.builder()
                .type(ValueType.INTEGER.getEncoding())
                .build();
    }

}
