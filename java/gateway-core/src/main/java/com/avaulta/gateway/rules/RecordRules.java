package com.avaulta.gateway.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.transforms.RecordTransform;
import com.fasterxml.jackson.annotation.JsonInclude;
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
        PARQUET,
        AUTO, // TODO: in v0.6, make this the default; and fail-back to file extension if no Content-Type provided
        ;

    }


    @Builder.Default
    Format format = Format.NDJSON; // TODO: in v0.6, default to AUTO

    /**
     * transforms to apply, in order.
     *
     * NOTE: list, to avoid ambiguity if had distinct list of redactions, pseudonymizations, etc
     *
     */
    @Singular
    List<RecordTransform> transforms;

    /**
     * Augments to compute and inject as synthetic sibling properties, run before transforms.
     *
     * @see <a href="file:///docs/development/augments.md">Augments Design Doc</a>
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Singular
    List<Augment> augments;

    /**
     * No-args constructor.
     * 1) Needed for Jackson deserialization.
     * 2) Explicit instantiation of @Singular fields required to avoid Lombok warnings about ignored default values.
     */
    public RecordRules() {
        this.transforms = Collections.emptyList();
        this.augments = new ArrayList<>();
    }

    //setter to ensure we get a List, even when coming through jackson
    public void setTransforms(List<RecordTransform> transforms) {
        if (transforms == null) {
            this.transforms = Collections.emptyList();
        } else {
            this.transforms = transforms;
        }
    }

    public void setAugments(List<Augment> augments) {
        if (augments == null) {
            this.augments = Collections.emptyList();
        } else {
            this.augments = augments;
        }
    }


}
