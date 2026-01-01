package com.avaulta.gateway.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.transforms.FieldTransformPipeline;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.extern.java.Log;

/**
 * rules for sanitizing "columnar" bulk files
 *
 * NOTE: user-facing documentation about these rules
 * @link "https://github.com/Worklytics/psoxy/tree/main/docs/bulk-file-sanitization.md"
 *
 *
 * so really this encodes two things that we could split:
 *   1) how to deserialize the bulk data into records
 *   2) how to sanitize the records
 *
 * @see RecordRules - which are a more comprehensive but less readable alternative
 */
@SuperBuilder(toBuilder = true)
@Log
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode
@JsonPropertyOrder(alphabetic = true)
@JsonIgnoreProperties("defaultScopeIdForSource") // so compatible with legacy CsvRules
public class ColumnarRules implements BulkDataRules {

    private static final long serialVersionUID = 1L;

    /**
     * delimiter of fields within serialized record
     *
     * in theory, `\t` should also work ...
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY) // this doesn't work
    @NonNull
    @Builder.Default
    protected Character delimiter = ',';

    /**
     * columns (fields) to duplicate
     *
     * NOTE: duplicates, if any, are applied BEFORE pseudonymization and transforms
     *
     * USE CASE: building lookup tables, where you want to duplicate column and then pseudonymize
     * one copy of it.Not really expected for typical 'bulk' file case.
     *
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Builder.Default
    @NonNull
    protected Map<String, String> columnsToDuplicate = new HashMap<>();


    //if we encode these as URL_SAFE_TOKEN, but not reversible then encoder won't prefix with 'p~'
    // and then not easy to tell difference bw pseudonym and the original employee_id value (but maybe we don't care ...)
    @Builder.Default
    protected PseudonymEncoder.Implementations pseudonymFormat =
        PseudonymEncoder.Implementations.JSON;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @NonNull
    @Singular(value = "columnToPseudonymize")
    protected List<String> columnsToPseudonymize = new ArrayList<>();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @NonNull
    @Singular(value = "columnToPseudonymizeIfPresent")
    protected List<String> columnsToPseudonymizeIfPresent = new ArrayList<>();


    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @NonNull
    @Singular(value = "columnToRedact")
    protected List<String> columnsToRedact = new ArrayList<>();

    /**
     * columns to rename
     *
     * USE CASE: source data doesn't match specification; rather than fixing data pipeline upstream,
     * or creating additional pipeline if you're repurposing existing data - just rename columns
     * as required.
     *
     * NOTE: renames, if any, are applied BEFORE pseudonymization and transforms
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Builder.Default
    @NonNull
    protected Map<String, String> columnsToRename = new HashMap<>();

    /**
     * if provided, only columns explicitly listed here will be included in output
     *  (inverse of columnsToRedact)
     *
     * USE CASE: if you don't control source data, and want to ensure that some unexpected column
     * that later appears in source doesn't get included in output.
     *
     * NOTE: due to semantics, this has a default value of 'null', rather than empty; thus its
     * behavior differs from most of the other fields
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    protected List<String> columnsToInclude;

    /**
     * **ALPHA FUNCTIONALITY; subject to backwards incompatible changes or removal**
     * should NOT be mixed with other column processing
     *
     * if provided, each FieldTransformPipeline will be applied to value of corresponding columns
     *
     * map of fieldName --> pipeline of transforms to apply to that field
     *
     */
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Builder.Default
    @NonNull
    protected Map<String, FieldTransformPipeline> fieldsToTransform = new HashMap<>();

}
