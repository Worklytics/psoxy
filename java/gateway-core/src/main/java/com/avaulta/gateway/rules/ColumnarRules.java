package com.avaulta.gateway.rules;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.fasterxml.jackson.annotation.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * rules for sanizitizing "columnar" bulk files
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
@JsonIgnoreProperties("defaultScopeIdForSource") // so compatiable with legacy CsvRules
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
     *
     */
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Builder.Default
    @NonNull
    protected Map<String, FieldTransformPipeline> fieldsToTransform = new HashMap<>();


    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) //for jackson
    @AllArgsConstructor(access = AccessLevel.PRIVATE) // for builder
    @Builder
    @Value
    public static class FieldTransformPipeline {

        /**
         * new field to write result as, if not being replaced
         *
         * q: ambiguous as to whether this is a 'rename' or creating a new field?
         *
         * currently, must be provided and transform pipelines are always creating a new column with this field name
         * (eg, not replacing existing column)
         */
        @NonNull
        String newName;

        /**
         * ordered list of transforms to apply to value
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @NonNull
        @Singular
        List<FieldValueTransform> transforms;

        @JsonIgnore
        public boolean isValid() {
            return StringUtils.isNotBlank(newName)
                    && transforms != null && transforms.stream().allMatch(FieldValueTransform::isValid);
        }
    }

    @Builder
    @Getter
    @FieldDefaults(makeFinal=true, level=AccessLevel.PRIVATE)
    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) //for jackson
    @AllArgsConstructor(access = AccessLevel.PRIVATE) // for builder
    @ToString
    @EqualsAndHashCode
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
        String filter;

        /**
         * if provided, value will be written using provided template
         *
         * expected to be a Java String format, with `%s` token; will be applied as String.format(template, match);
         *
         * TODO: expect this to change after 'alpha' version
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String formatString;

        /**
         * if provided, value will be pseudonymized with provided scope
         *
         * @deprecated ; only relevant for legacy case
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String pseudonymizeWithScope;

        @JsonIgnore
        public boolean isValid() {

            boolean exactlyOneNonNull = Stream.of(filter, formatString, pseudonymizeWithScope)
                .filter(Objects::nonNull)
                .count() == 1;


            if (filter != null) {
                try {
                    Pattern pattern = Pattern.compile(filter);
                } catch (PatternSyntaxException e) {
                    log.warning("invalid regex: " + filter);
                    return false;
                }
            }

            if (formatString != null) {
                if (!formatString.contains("%s")) {
                    log.warning("formatString must contain '%s' token: " + formatString);
                    return false;
                }
                if (Pattern.compile("%s").matcher(formatString).results().count() > 1) {
                    log.warning("formatString must contain exactly one '%s' token: " + formatString);
                    return false;
                }
            }

            return exactlyOneNonNull;
        }

        public static FieldValueTransform filter(String filter) {
            return FieldValueTransform.builder().filter(filter).build();
        }

        public static FieldValueTransform pseudonymizeWithScope(String scope) {
            return FieldValueTransform.builder().pseudonymizeWithScope(scope).build();
        }

        public static FieldValueTransform formatString(String template) {
            return FieldValueTransform.builder().formatString(template).build();
        }


    }
}
