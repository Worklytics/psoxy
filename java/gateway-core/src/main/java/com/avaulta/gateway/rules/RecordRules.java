package com.avaulta.gateway.rules;

import com.avaulta.gateway.rules.transforms.RecordTransform;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.List;

/**
 * **alpha**
 *
 * rules for sanitizing "record" data from a bulk file
 *
 * This is more general structure, allowing for possibly nested transforms on fields
 *
 * @see ColumnarRules, which can provide a subset of equivalent functionality and is better for
 * simple cases
 *
 *
 */
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@SuperBuilder(toBuilder = true)
@Data
public class RecordRules implements BulkDataRules {

    /**
     * format of bulk 'Record' data; represents both how to split file into records AND how to parse
     * records.
     *
     */
    public enum Format {
        NDJSON_RELAXED,
        NDJSON,
        CSV,
        ;
        //AVRO?

    }


    @Builder.Default
    Format format = Format.NDJSON;

    /**
     * transforms to apply, in order.
     *
     * NOTE: list, to avoid ambiguity if had distinct list of redactions, pseudonymizations, etc
     *
     */
    @Singular
    List<RecordTransform> transforms = Collections.emptyList();

    //setter to ensure we get a List, even when coming through jackson
    public void setTransforms(List<RecordTransform> transforms) {
        if (transforms == null) {
            this.transforms = Collections.emptyList();
        } else {
            this.transforms = transforms;
        }
    }


}
