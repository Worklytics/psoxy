package com.avaulta.gateway.rules;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.java.Log;

import java.util.*;

/**
 * NOTE: user-facing documentation about these rules
 * @link "https://github.com/Worklytics/psoxy/tree/main/docs/bulk-file-sanitization.md"
 *
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
    @JsonInclude(JsonInclude.Include.NON_EMPTY) //this works ...
    @Builder.Default
    @NonNull
    protected Map<String, String> columnsToDuplicate = new HashMap<>();


    //if we encode these as URL_SAFE_TOKEN, but not reversible then encoder won't prefix with 'p~'
    // and then not easy to tell difference bw pseudonym and the original employee_id value (but maybe we don't care ...)
    @Builder.Default
    protected PseudonymEncoder.Implementations pseudonymFormat =
        PseudonymEncoder.Implementations.JSON;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @NonNull
    @Singular(value = "columnToPseudonymize")
    protected List<String> columnsToPseudonymize = new ArrayList<>();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
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
    @Builder.Default
    @NonNull
    protected Map<String, String> columnsToRename = new HashMap<>();

    /**
     * if provided, only columns explicitly listed here will be included in output
     *  (inverse of columnsToRedact)
     *
     * USE CASE: if you don't control source data, and want to ensure that some unexpected column
     * that later appears in source doesn't get included in output.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected List<String> columnsToInclude;

    /**
     * **ALPHA FUNCTIONALITY; subject to backwards incompatible changes or removal**
     *
     * if provided, each FieldValueTransform will be applied to value of corresponding columns
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Builder.Default
    @NonNull
    protected Map<String, FieldValueTransform> columnsToTransform = new HashMap<>();


    /**
     * **ALPHA FUNCTIONALITY; subject to backwards incompatible changes or removal**
     *
     * map of columns to pseudonymize, with scope to use when pseudonymizing; only alters behavior
     * in LEGACY cases
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Builder.Default
    @NonNull
    protected Map<String, String> columnsToPseudonymizeWithScope = new HashMap<>();

    //do we care about supporting format-preserving pseudonymization??

    @Builder
    @Value
    public static class FieldValueTransform {


        /**
         * if provided, filter regex will be applied and only values matching filter will be
         * preserved; not matching values will be redacted.
         *
         * if regex includes a capturing group, then only portion of value matched by the first
         * capturing group will be preserved.
         *
         * NOTE: use-case for omitting is to pseudonymize column with a specific scope
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String filterRegex;

        /**
         * if provided, value will be written using provided template
         *
         * expected to be a Java String format, with `%s` token; will be applied as String.format(template, match);
         *
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String outputTemplate;
    }
}
