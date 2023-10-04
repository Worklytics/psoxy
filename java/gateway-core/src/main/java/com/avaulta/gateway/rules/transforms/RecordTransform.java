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
 *  - !<redact>
 *    path: "foo"
 *
 * would prefer that both
 *
 *  - redact: "foo"
 *
 * and
 *  - redact
 *    path: "foo"
 *
 * would be supported as equivalent ... but can't get that to work with Jackson YAML
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.WRAPPER_OBJECT,
    defaultImpl = RecordTransform.class
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RecordTransform.Redact.class, name = "redact"),
    @JsonSubTypes.Type(value = RecordTransform.Pseudonymize.class, name = "pseudonymize")
})
public interface RecordTransform {


    /**
     * json path to field to transform
     */
    String getPath();

    @NoArgsConstructor
    @Data
    @SuperBuilder(toBuilder = true)
    abstract class BaseRecordTransform implements RecordTransform {

        private String path;
    }



    @JsonTypeName("redact")
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    @Data
    @EqualsAndHashCode(callSuper = true)
    class Redact extends BaseRecordTransform {

    }


    @JsonTypeName("pseudonymize")
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    @Data
    @EqualsAndHashCode(callSuper = true)
    class Pseudonymize extends BaseRecordTransform {

    }
}
