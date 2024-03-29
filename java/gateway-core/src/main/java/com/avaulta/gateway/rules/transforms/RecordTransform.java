package com.avaulta.gateway.rules.transforms;

import com.fasterxml.jackson.annotation.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;


/**
 *
 * a transform to apply within a record, with a mandatory JSON path that specifies the field(s) to
 * be transformed
 *
 * yaml representation
 *  - redact: "foo"
 *
 * can we make this polymorphic, so the following also works?
 *   - redact:
 *       - "foo"
 *       - "bar"
 *   - redact: "foo"
 * --> probably with a custom deserializer
 *
 *
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.DEDUCTION,
    defaultImpl = RecordTransform.class
)
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
