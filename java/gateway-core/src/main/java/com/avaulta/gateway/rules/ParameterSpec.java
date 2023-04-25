package com.avaulta.gateway.rules;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParameterSpec {

    String name;

    ParameterSchema schema;

    // subset of JsonSchema for now; in theory, *should* be the full thing but that starts to get
    // a little crazy for PathParam/QueryParam use case
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParameterSchema {

        String type;

        // in theory, `null`, numbers, or Strings all valid
        // see: https://json-schema.org/understanding-json-schema/reference/generic.html#enumerated-values
        @JsonProperty("enum")
        List<Object> enumValues;

        static ParameterSchema type(String type) {
            return ParameterSchema.builder().type(type).build();
        }
    }
}
