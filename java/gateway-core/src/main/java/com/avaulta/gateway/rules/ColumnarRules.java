package com.avaulta.gateway.rules;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.transforms.RecordTransform;
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

    /**
     * defines a pipeline for transforming a single field
     */
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
        @Deprecated //split into renameTo, copyTo cases for clarity
        String newName;

        // TODO: renameTo:

        // TODO: copyTo:

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

    /**
     * a transform to operate on a single field value
     *
     */
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.DEDUCTION,
        defaultImpl = RecordTransform.class
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = FieldValueTransform.Filter.class),
        @JsonSubTypes.Type(value = FieldValueTransform.FormatString.class),
        @JsonSubTypes.Type(value = FieldValueTransform.PseudonymizeWithScope.class),
    })
    public interface FieldValueTransform {

        @JsonIgnore
        boolean isValid();

        static FieldValueTransform filter(String filter) {
            return Filter.builder().filter(filter).build();
        }

        static FieldValueTransform pseudonymizeWithScope(String scope) {
            return PseudonymizeWithScope.builder().pseudonymizeWithScope(scope).build();
        }

        static FieldValueTransform formatString(String template) {
            return FormatString.builder().formatString(template).build();
        }

        /**
         * if provided, value will be written using provided template
         *
         * expected to be a Java String format, with `%s` token; will be applied as String.format(template, match);
         *
         * TODO: expect this to change after 'alpha' version
         */
        @JsonTypeName("formatString")
        @NoArgsConstructor
        @SuperBuilder(toBuilder = true)
        @Data
        class FormatString implements FieldValueTransform {
            String formatString;

            @Override
            public boolean isValid() {
                    if (!formatString.contains("%s")) {
                        log.warning("formatString must contain '%s' token: " + formatString);
                        return false;
                    }
                    if (Pattern.compile("%s").matcher(formatString).results().count() > 1) {
                        log.warning("formatString must contain exactly one '%s' token: " + formatString);
                        return false;
                    }
                return true;
            }
        }

        /**
         * if provided, filter regex will be applied and only values matching filter will be
         * preserved; not matching values will be redacted.
         *
         * if regex includes a capturing group, then only portion of value matched by the first
         * capturing group will be preserved.
         *
         * NOTE: use-case for omitting is to pseudonymize column with a specific scope
         */
        @JsonTypeName("filter")
        @NoArgsConstructor
        @SuperBuilder(toBuilder = true)
        @Data
        class Filter implements FieldValueTransform {
            @NonNull
            String filter;

            @Override
            public boolean isValid() {
                try {
                    Pattern pattern = Pattern.compile(filter);
                } catch (PatternSyntaxException e) {
                   log.warning("invalid regex: " + filter);
                    return false;
                }

                return true;
            }
        }

        /**
         * if provided, value will be pseudonymized with provided scope
         *
         * @deprecated ; only relevant for legacy case
         */
        @JsonTypeName("pseudonymizeWithScope")
        @NoArgsConstructor
        @SuperBuilder(toBuilder = true)
        @Data
        class PseudonymizeWithScope implements FieldValueTransform {

            @NonNull
            String pseudonymizeWithScope;

            @Override
            public boolean isValid() {
                return pseudonymizeWithScope != null;
            }
        }


        class Pseudonymize {
            String pseudonymize = "auto";
        }

        class EncryptWithKey {


            String encryptWithKey;
        }

        class DecryptWithKey {

            String decryptWithKey;
        }
    }
}
