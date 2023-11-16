package com.avaulta.gateway.rules.transforms;

import com.fasterxml.jackson.annotation.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;


/**
 *
 * a transform to apply within a record
 *
 * yaml representation
 *  - redact: "foo"
 *
 * would be supported as equivalent ... but can't get that to work with Jackson YAML
 */

@JsonSubTypes({
    @JsonSubTypes.Type(value = RecordTransform.Redact.class),
    @JsonSubTypes.Type(value = RecordTransform.Pseudonymize.class)
})
public interface RecordTransform {


    /**
     * json path to field to transform
     */
    String getPath();


    @JsonTypeName("redact")
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    @Data
    class Redact implements RecordTransform {

        /**
         * json path to field to redact
         */
        String redact;

        @Override
        @JsonIgnore
        public String getPath() {
            return redact;
        }
    }


    @JsonTypeName("pseudonymize")
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    @Data
    class Pseudonymize implements RecordTransform {

        /**
         * json path to field to pseudonymize
         */
        String pseudonymize;

        @Override
        @JsonIgnore
        public String getPath() {
            return pseudonymize;
        }
    }
}
