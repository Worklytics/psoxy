package com.avaulta.gateway.rules;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParameterSchemaUtilsTest {


    String EXAMPLE_REVERSIBLE = "p~nVPSMYD7ZO_ptGIMJ65TAFo5_vVVQQ2af5Bfg7bW0Jq9JIOXfBWhts_zA5Ns0r4m";


    ParameterSchemaUtils parameterSchemaUtils = new ParameterSchemaUtils();
    @Test
    public void validation_reversiblePseudonym() {
        ParameterSchema parameterSchema = ParameterSchema.builder()
                .type(ParameterSchema.ValueType.STRING.getEncoding())
                .format(ParameterSchema.StringFormat.REVERSIBLE_PSEUDONYM.getStringEncoding())
                .build();

        assertTrue(parameterSchemaUtils.validate(parameterSchema, EXAMPLE_REVERSIBLE));
        assertFalse(parameterSchemaUtils.validate(parameterSchema, "not-a-pseudonym"));
        assertTrue(parameterSchemaUtils.validate(parameterSchema, null));
    }


    @Test
    public void validation_enum() {
        ParameterSchema parameterSchema = ParameterSchema.builder()
                .type(ParameterSchema.ValueType.STRING.getEncoding())
                .enumValues(Arrays.asList("v0.1", "beta"))
                .build();

        assertTrue(parameterSchemaUtils.validate(parameterSchema, "beta"));
        assertTrue(parameterSchemaUtils.validate(parameterSchema, "v0.1"));
        assertFalse(parameterSchemaUtils.validate(parameterSchema, "v0.2"));

        assertTrue(parameterSchemaUtils.validate(parameterSchema, null));
    }

    @Test
    public void validation_number() {
        ParameterSchema parameterSchema = ParameterSchema.builder()
                .type(ParameterSchema.ValueType.NUMBER.getEncoding())
                .build();

        assertTrue(parameterSchemaUtils.validate(parameterSchema, "1.0"));
        assertTrue(parameterSchemaUtils.validate(parameterSchema, "1"));
        assertTrue(parameterSchemaUtils.validate(parameterSchema, "0.423412343123"));
        assertFalse(parameterSchemaUtils.validate(parameterSchema, "not-a-number"));
        assertTrue(parameterSchemaUtils.validate(parameterSchema, null));
    }

    @Test
    public void validate_all() {
        Map<String, ParameterSchema> parameterSchemas =
                Map.of("string", ParameterSchema.string(),
                        "number", ParameterSchema.builder().type("number").build(),
                        "reversible", ParameterSchema.reversiblePseudonym());

        assertTrue(parameterSchemaUtils.validateAll(parameterSchemas, Arrays.asList(
                Pair.of("string", "string"),
                Pair.of("number", "1.0"),
                Pair.of("reversible", EXAMPLE_REVERSIBLE)
        )));

        assertFalse(
                parameterSchemaUtils.validateAll(parameterSchemas, Arrays.asList(
                        Pair.of("string", "string"),
                        Pair.of("number", "not-a-number"),
                        Pair.of("reversible", EXAMPLE_REVERSIBLE)
                )));
        assertFalse(
                parameterSchemaUtils.validateAll(parameterSchemas, Arrays.asList(

                        Pair.of("number", "not-a-number"),
                        Pair.of("reversible", "not-a-pseudonym")
                )));
    }
}


