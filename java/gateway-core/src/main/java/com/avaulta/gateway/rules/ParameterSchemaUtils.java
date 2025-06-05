package com.avaulta.gateway.rules;

import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * utils for validating values conform to parameter schemas
 *
 * NOTE: unlike JsonSchemaFilterUtils, as that *redacts* values that don't match schema; this validates that a value
 * matches schema, more analogous to how standard JsonSchema is meant to be used.
 *
 */
public class ParameterSchemaUtils {


    /**
     * validate that a value conforms to a schema
     * @param schema to validate against
     * @param value to validate
     * @return whether value conforms to schema
     */
    public boolean validate(ParameterSchema schema, String value) {
        if (value != null) {
            if (schema.getType() != null) {
                if (schema.getType().equals(ParameterSchema.ValueType.INTEGER.getEncoding())) {
                    return Pattern.compile("\\d+").matcher(StringUtils.trimToEmpty(value)).matches();
                } else if (schema.getType().equals(ParameterSchema.ValueType.NUMBER.getEncoding())) {
                    try {
                        Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }

            if (Objects.equals(schema.getFormat(), ParameterSchema.StringFormat.REVERSIBLE_PSEUDONYM.getStringEncoding())) {
                if (!UrlSafeTokenPseudonymEncoder.REVERSIBLE_PSEUDONYM_PATTERN.matcher(value).matches()) {
                    return false;
                }
            }

            if (schema.getEnumValues() != null) {
                if (!schema.getEnumValues().contains(value)) {
                    return false;
                }
            }

            if (schema.getPattern() != null) {
                if (!Pattern.compile(schema.getPattern()).matcher(value).matches()) {
                    return false;
                }
            }

            if (ObjectUtils.isNotEmpty(schema.getOrs())) {
                return schema.getOrs().stream().anyMatch(
                    orSubSchema -> validate(orSubSchema, value));
            }
        }
        return true;
    }

    /**
     * validate that all binding of a parameter defined in bindings for a parameter with a defined schema in schemas
     * is valid per that schema.
     * - bindings for parameters w/o defined schema are considered valid
     * - parameters with defined schemas are not required to have a binding
     *
     * @param schemas  parameter names --> schema
     * @param bindings parameter names --> values
     * @return whether all bindings are valid for parameters with schema defined in schemas
     */
    public boolean validateAll(Map<String, QueryParameterSchema> schemas,
                               List<Pair<String, String>> bindings) {
        return schemas.entrySet().stream()
                // all values, for all parameters that have defined schema, are valid
                .allMatch(paramSchema -> {

                    List<String> values = bindings.stream()
                        .filter(p -> p.getKey().equals(paramSchema.getKey()))
                        .map(Pair::getValue)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toUnmodifiableList());

                    boolean required = Optional.ofNullable(paramSchema.getValue().getRequired()).orElse(false) ;
                    boolean hasAValue = !values.isEmpty();

                     return (!required || hasAValue) &&
                         values.stream().allMatch(value -> validate(paramSchema.getValue(), value));
  });
    }




}
