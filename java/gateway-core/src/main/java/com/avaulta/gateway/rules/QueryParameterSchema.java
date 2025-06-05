package com.avaulta.gateway.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.experimental.SuperBuilder;


@JsonPropertyOrder({"type", "format", "pattern", "enum", "required"})
@SuperBuilder(toBuilder = true)
@With
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
public class QueryParameterSchema extends ParameterSchema {

    /**
     * whether this parameter is required
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Boolean required;

    public static QueryParameterSchema string() {
        return QueryParameterSchema.builder()
            .type(ValueType.STRING.getEncoding())
            .build();
    }

    public static QueryParameterSchema reversiblePseudonym() {
        return QueryParameterSchema.builder()
            .type(ValueType.STRING.getEncoding())
            .format(StringFormat.REVERSIBLE_PSEUDONYM.getStringEncoding())
            .build();
    }

    public static QueryParameterSchema integer() {
        return QueryParameterSchema.builder()
            .type(ValueType.INTEGER.getEncoding())
            .build();
    }
}
