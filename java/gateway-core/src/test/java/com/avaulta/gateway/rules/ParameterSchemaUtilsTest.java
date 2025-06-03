package com.avaulta.gateway.rules;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
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
    public void validation_integer() {
        ParameterSchema parameterSchema = ParameterSchema.builder()
                .type(ParameterSchema.ValueType.INTEGER.getEncoding())
                .build();

        assertTrue(parameterSchemaUtils.validate(parameterSchema, "1"), "integers should be allowed");
        assertTrue(parameterSchemaUtils.validate(parameterSchema, String.valueOf( Long.MAX_VALUE)), "max long value should be allowed");
        assertTrue(parameterSchemaUtils.validate(parameterSchema, "13281932123987132987123981239"), "even longer integers should be allowed");
        assertTrue(parameterSchemaUtils.validate(parameterSchema, "0"));
        assertFalse(parameterSchemaUtils.validate(parameterSchema, "1.0"));
        assertFalse(parameterSchemaUtils.validate(parameterSchema, "not-a-number"));
        assertTrue(parameterSchemaUtils.validate(parameterSchema, null));
        assertFalse(parameterSchemaUtils.validate(parameterSchema, "0.423412343123"));

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
        ), true));

        assertFalse(
                parameterSchemaUtils.validateAll(parameterSchemas, Arrays.asList(
                        Pair.of("string", "string"),
                        Pair.of("number", "not-a-number"),
                        Pair.of("reversible", EXAMPLE_REVERSIBLE)
                ), true));
        assertFalse(
                parameterSchemaUtils.validateAll(parameterSchemas, Arrays.asList(

                        Pair.of("number", "not-a-number"),
                        Pair.of("reversible", "not-a-pseudonym")
                ), true));
    }


    @Test
    public void validate_or(){
        ParameterSchema schema = ParameterSchema.builder()
            .or(ParameterSchema.builder().pattern("^all$").build())
            .or(ParameterSchema.integer())
            .build();

        assertTrue(parameterSchemaUtils.validate(schema, "all"));
        assertTrue(parameterSchemaUtils.validate(schema, "123"));
        assertFalse(parameterSchemaUtils.validate(schema, "any"));
    }

    @Test
    public void required() {
        ParameterSchema schema = ParameterSchema.builder()
            .type(ParameterSchema.ValueType.STRING.getEncoding())
            .required(true)
            .build();

        assertTrue(parameterSchemaUtils.validateAll(Map.of("foo", schema), Arrays.asList(Pair.of("foo", "bar")), false));

        assertFalse(parameterSchemaUtils.validateAll(Map.of("foo", schema), Arrays.asList(Pair.of("foo", null)), false));
        assertFalse(parameterSchemaUtils.validateAll(Map.of("foo", schema), Arrays.asList(Pair.of("foo2", "bar")), false));
        assertFalse(parameterSchemaUtils.validateAll(Map.of("foo", schema), Collections.emptyList(), false));
    }
}


