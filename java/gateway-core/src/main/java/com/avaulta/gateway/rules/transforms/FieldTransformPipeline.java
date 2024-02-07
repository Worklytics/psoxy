package com.avaulta.gateway.rules.transforms;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * defines a pipeline for transforming a single field
 */
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) //for jackson
@AllArgsConstructor(access = AccessLevel.PRIVATE) // for builder
@Builder
@Value
public class FieldTransformPipeline {

    /**
     * new field to write result as, if not being replaced
     * <p>
     * q: ambiguous as to whether this is a 'rename' or creating a new field?
     * <p>
     * currently, must be provided and transform pipelines are always creating a new column with this field name
     * (eg, not replacing existing column)
     */
    @Deprecated //split into renameTo, copyTo cases for clarity
    String newName;


    /**
     * if provided, will look up the column using that, otherwise will assume that the key of the map
     * that the transform is in refers to the source column (for backwards compatibility)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String sourceColumn;

    // TODO: renameTo:

    // TODO: copyTo:

    /**
     * ordered list of transforms to apply to value
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @NonNull
    @Singular
    List<FieldTransform> transforms;

    @JsonIgnore
    public boolean isValid() {
        return StringUtils.isNotBlank(newName)
            && transforms != null && transforms.stream().allMatch(FieldTransform::isValid);
    }
}
