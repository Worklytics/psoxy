package com.avaulta.gateway.rules.transforms;

import com.fasterxml.jackson.annotation.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * a transform to operate on a single field value
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.DEDUCTION
    //defaultImpl = FieldTransform.class
)
@JsonSubTypes({
    @JsonSubTypes.Type(FieldTransform.JavaRegExpReplace.class),
    @JsonSubTypes.Type(FieldTransform.Filter.class),
    @JsonSubTypes.Type(FieldTransform.FormatString.class),
    @JsonSubTypes.Type(FieldTransform.PseudonymizeWithScope.class),
    @JsonSubTypes.Type(FieldTransform.Pseudonymize.class) })
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

    static FieldTransform javaRegExpReplace(String re, String replace) {
        JavaRegExpReplace.Config config = JavaRegExpReplace.Config.builder().regExp(re).replace(replace).build();
        return JavaRegExpReplace.builder().javaRegExpReplace(config).build();
    }

    static FieldTransform pseudonymize(boolean pseudonymize) {
        return Pseudonymize.builder().pseudonymize(pseudonymize).build();
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
     * if provided, value will be replaced using provided regex
     * Note: the parameter name must match the name of the class otherwise it will not be deserialized
     */
    @JsonTypeName("javaRegExpReplace")
    @NoArgsConstructor
    @AllArgsConstructor
    @SuperBuilder(toBuilder = true)
    @Data
    @Log
    class JavaRegExpReplace implements FieldTransform {

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        static class Config {
            @NonNull
            @JsonProperty("regExp")
            String regExp;
            @NonNull
            @JsonProperty("replace")
            String replace;
        }

        Config javaRegExpReplace;

        @JsonIgnore
        public String getRegExp() {
            return javaRegExpReplace.getRegExp();
        }

        @JsonIgnore
        public String getReplaceString() {
            return javaRegExpReplace.getReplace();
        }

        @Override
        public boolean isValid() {
            String regExp = getRegExp();
            try {
                Pattern pattern = Pattern.compile(regExp);
            } catch (PatternSyntaxException e) {
                log.warning("invalid regex: " + regExp);
                return false;
            }
            return true;
        }
    }

    /**
     * if provided, value will be replaced using provided regex
     * Note: the parameter name must match the name of the class otherwise it will not be deserialized
     */
    @JsonTypeName("pseudonymize")
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    @Data
    @Log
    class Pseudonymize implements FieldTransform {

        @NonNull
        boolean pseudonymize;

        @Override
        public boolean isValid() {
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
