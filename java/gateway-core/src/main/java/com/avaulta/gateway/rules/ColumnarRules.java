package com.avaulta.gateway.rules;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.java.Log;

import java.util.*;

/**
 *
 * so really this encodes two things that we could split:
 *   1) how to deserialize the bulk data into records
 *   2) how to sanitize the records
 *
 */
@SuperBuilder(toBuilder = true)
@Log
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ColumnarRules implements BulkDataRules {

    private static final long serialVersionUID = 1L;

    /**
     * delimiter of fields within serialized record
     *
     * in theory, `\t` should also work ...
     */
    @Builder.Default
    protected Character delimiter = ',';

    /**
     * columns (fields) to duplicate
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Builder.Default
    @NonNull
    protected Map<String, String> columnsToDuplicate = new HashMap<>();


    //if we encode these as URL_SAFE_TOKEN, but not reversible then encoder won'd prefix with 'p~'
    // and then not easy to tell difference bw pseudonym and the original  employee_id value (but maybe we don't care ...)
    @Builder.Default
    protected PseudonymEncoder.Implementations pseudonymFormat = PseudonymEncoder.Implementations.JSON;

    @NonNull
    @Singular(value = "columnToPseudonymize")
    protected List<String> columnsToPseudonymize = new ArrayList<>();

    @NonNull
    @Singular(value = "columnToRedact")
    protected List<String> columnsToRedact = new ArrayList<>();

    /**
     * columns to rename
     *
     * NOTE: renames, if any, are applied BEFORE pseudonymization
     */
    @Builder.Default
    @NonNull
    protected Map<String, String> columnsToRename = new HashMap<>();

    /**
     * if provided, only columns explicitly listed here will be included in output
     *  (inverse of columnsToRedact)
     *
     * use case: if you don't control source, and want to ensure that some unexpected column that
     * later appears in source doesn't get included in output.
     */
    protected List<String> columnsToInclude;
}
