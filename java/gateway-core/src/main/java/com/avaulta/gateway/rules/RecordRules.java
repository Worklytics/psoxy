package com.avaulta.gateway.rules;

import com.avaulta.gateway.rules.transforms.RecordTransform;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@SuperBuilder(toBuilder = true)
@Data
public class RecordRules implements BulkDataRules {

    enum Format {
        NDJSON;
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


}
