package com.avaulta.gateway.rules.transforms;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Augment.WithLLM.class, name = "llm")
})
@SuperBuilder(toBuilder = true)
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode(callSuper = false)
public abstract class Augment {

    //NOTE: this is filled for JSON, but for YAML a YAML-specific type syntax is used:
    // !<llm>
    // Jackson YAML can still *read* yaml-encoded augment with `method: "llm"`
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String method;

    /**
     * specifies JSON paths within content that identify value to be used for augmentation
     * <p>
     * each value that matches each of the paths will be passed to augment's function as a
     * separate invocation.
     * <p>
     * The matched value will NOT be mutated; instead, the augmentation result will be added
     * as a new JSON node at the same level in the JSON tree.
     * <p>
     * supported for JSON
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Singular
    List<String> jsonPaths;

    /**
     * name of the field to add with the augmented data
     * <p>
     * if not specified, a default name will be used based on the augmentation method
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String outputFieldName;

    @NoArgsConstructor //for jackson
    @SuperBuilder(toBuilder = true)
    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class WithLLM extends Augment {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        String prompt;

        public static WithLLM ofPaths(String... jsonPaths) {
            return WithLLM.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }

        public WithLLM clone() {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(this.jsonPaths != null ? new ArrayList<>(this.jsonPaths) : new ArrayList<>())
                .build();
        }
    }

    public abstract Augment clone();

}

