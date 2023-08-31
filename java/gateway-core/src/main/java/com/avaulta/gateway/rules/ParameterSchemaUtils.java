package com.avaulta.gateway.rules;

import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * utils for validating values conform to parameter schemas
 *
 * NOTE: unlike JsonSchemaFilterUtils, as that *redacts* values that don't match schema; this validates that a value
 * matches schema, more analogous to how standard JsonSchema is meant to be used.
 *
 */
public class ParameterSchemaUtils {

    public boolean validate(ParameterSchema schema, String value) {
        if (value != null) {
            if (schema.getType() != null) {
                if (schema.getType().equals(ParameterSchema.ValueType.INTEGER.getEncoding())) {
                    try {
                        Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return false;
                    }
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
        }
        return true;
    }
}
