package com.avaulta.gateway.rules.transforms;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * a transform to operate on a single field value
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.DEDUCTION,
    defaultImpl = FieldTransform.class
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = FieldTransform.Filter.class),
    @JsonSubTypes.Type(value = FieldTransform.FormatString.class),
    @JsonSubTypes.Type(value = FieldTransform.PseudonymizeWithScope.class),
})
public interface FieldTransform {

    @JsonIgnore
    boolean isValid();

    static FieldTransform filter(String filter) {
        return Filter.builder().filter(filter).build();
    }

    static FieldTransform pseudonymizeWithScope(String scope) {
        return PseudonymizeWithScope.builder().pseudonymizeWithScope(scope).build();
    }

    static FieldTransform formatString(String template) {
        return FormatString.builder().formatString(template).build();
    }

    /**
     * if provided, value will be written using provided template
     * <p>
     * expected to be a Java String format, with `%s` token; will be applied as String.format(template, match);
     * <p>
     * TODO: expect this to change after 'alpha' version
     */
    @JsonTypeName("formatString")
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    @Data
    @Log
    class FormatString implements FieldTransform {

        @NonNull
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
     * <p>
     * if regex includes a capturing group, then only portion of value matched by the first
     * capturing group will be preserved.
     * <p>
     * NOTE: use-case for omitting is to pseudonymize column with a specific scope
     */
    @JsonTypeName("filter")
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    @Data
    @Log
    class Filter implements FieldTransform {

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
    class PseudonymizeWithScope implements FieldTransform {

        @NonNull
        String pseudonymizeWithScope;

        @Override
        public boolean isValid() {
            return StringUtils.isNotBlank(pseudonymizeWithScope);
        }
    }
}
