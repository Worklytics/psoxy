package com.avaulta.gateway.rules;

import java.util.Collections;
import java.util.List;
import com.avaulta.gateway.rules.transforms.RecordTransform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

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

@SuperBuilder(toBuilder = true)
@Data
public class RecordRules implements BulkDataRules {

    /**
     * format of bulk 'Record' data; represents both how to split file into records AND how to parse
     * records.
     *
     * TODO: consider dropping this entirely in v0.6 ???
     */
    public enum Format {
        NDJSON,
        CSV,
        JSON_ARRAY,
        AUTO, // TODO: in v0.6, make this the default; and fail-back to file extension if no Content-Type provided
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
    List<RecordTransform> transforms;

    /**
     * No-args constructor.
     * 1) Needed for Jackson deserialization.
     * 2) Explicit instantiation of @Singular fields required to avoid Lombok warnings about ignored default values.
     */
    public RecordRules() {
        this.transforms = Collections.emptyList();
    }

    //setter to ensure we get a List, even when coming through jackson
    public void setTransforms(List<RecordTransform> transforms) {
        if (transforms == null) {
            this.transforms = Collections.emptyList();
        } else {
            this.transforms = transforms;
        }
    }


}
