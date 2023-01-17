package com.avaulta.gateway.rules;

import com.avaulta.gateway.rules.transform.Pseudonymize;
import com.avaulta.gateway.rules.transform.Transform;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RESTRulesPocTest {

    ObjectMapper objectMapper;
    ObjectMapper yamlMapper;
    JsonSchemaGenerator jsonSchemaGenerator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        yamlMapper = new YAMLMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        jsonSchemaGenerator = new JsonSchemaGenerator(objectMapper);
    }

    @Test
    void simplePojoWithoutCustomSerializer_json() throws JsonProcessingException {

        JsonNode jsonSchema = jsonSchemaGenerator.generateJsonSchema(SimplePojo.class);

        String jsonSchemaAsString = objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(jsonSchema);

        //we want these serialized as ISO strings, so shouldn't have definitions
        assertFalse(jsonSchemaAsString.contains("#/definitions/Instant"));
        assertFalse(jsonSchemaAsString.contains("#/definitions/LocalDate"));

        assertEquals(SimplePojo.EXPECTED_SCHEMA,
            jsonSchemaAsString);

        JsonSchemaWithTransforms schemaWithTransforms =
            objectMapper.convertValue(jsonSchema, JsonSchemaWithTransforms.class);

        //q: how to do this with annotation on original POJO??
        schemaWithTransforms.getProperties().get("someString")
            .addTransform(Pseudonymize.builder().build());

        assertEquals(SimplePojo.EXPECTED_SCHEMA_WITH_TRANSFORMS,
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schemaWithTransforms));
    }

    @Test
    void simplePojoWithoutCustomSerializer_yaml() throws JsonProcessingException {

        JsonNode jsonSchema = jsonSchemaGenerator.generateJsonSchema(SimplePojo.class);

        JsonSchemaWithTransforms schemaWithTransforms =
            yamlMapper.convertValue(jsonSchema, JsonSchemaWithTransforms.class);

        //q: how to do this with annotation on original POJO??
        schemaWithTransforms.getProperties().get("someString")
            .addTransform(Pseudonymize.builder().build());

        assertEquals(SimplePojo.EXPECTED_SCHEMA_YAML,
            yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));

        assertEquals(SimplePojo.EXPECTED_SCHEMA_WITH_TRANSFORMS_YAML,
            yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schemaWithTransforms));
    }


    @Data
    static class SimplePojo {

        String someString;
        LocalDate date;

        Instant timestamp;

        //would using https://github.com/mbknor/mbknor-jackson-jsonSchema allow us to add some
        // custom properties to the schema, including transforms list on properties?

        static final String EXPECTED_SCHEMA = "{\n" +
            "  \"$schema\" : \"http://json-schema.org/draft-04/schema#\",\n" +
            "  \"title\" : \"Simple Pojo\",\n" +
            "  \"type\" : \"object\",\n" +
            "  \"additionalProperties\" : false,\n" +
            "  \"properties\" : {\n" +
            "    \"someString\" : {\n" +
            "      \"type\" : \"string\"\n" +
            "    },\n" +
            "    \"date\" : {\n" +
            "      \"type\" : \"string\",\n" +
            "      \"format\" : \"date\"\n" +
            "    },\n" +
            "    \"timestamp\" : {\n" +
            "      \"type\" : \"string\",\n" +
            "      \"format\" : \"date-time\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

        static final String EXPECTED_SCHEMA_WITH_TRANSFORMS = "{\n" +
            "  \"type\" : \"object\",\n" +
            "  \"additionalProperties\" : false,\n" +
            "  \"properties\" : {\n" +
            "    \"someString\" : {\n" +
            "      \"type\" : \"string\",\n" +
            "      \"transforms\" : [ {\n" +
            "        \"method\" : \"pseudonymize\",\n" +
            "        \"encoding\" : \"JSON\"\n" +
            "      } ]\n" +
            "    },\n" +
            "    \"date\" : {\n" +
            "      \"type\" : \"string\",\n" +
            "      \"format\" : \"date\"\n" +
            "    },\n" +
            "    \"timestamp\" : {\n" +
            "      \"type\" : \"string\",\n" +
            "      \"format\" : \"date-time\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

        static final String EXPECTED_SCHEMA_YAML = "---\n" +
            "$schema: \"http://json-schema.org/draft-04/schema#\"\n" +
            "title: \"Simple Pojo\"\n" +
            "type: \"object\"\n" +
            "additionalProperties: false\n" +
            "properties:\n" +
            "  someString:\n" +
            "    type: \"string\"\n" +
            "  date:\n" +
            "    type: \"string\"\n" +
            "    format: \"date\"\n" +
            "  timestamp:\n" +
            "    type: \"string\"\n" +
            "    format: \"date-time\"\n";

        static final String EXPECTED_SCHEMA_WITH_TRANSFORMS_YAML = "---\n" +
            "type: \"object\"\n" +
            "additionalProperties: false\n" +
            "properties:\n" +
            "  someString:\n" +
            "    type: \"string\"\n" +
            "    transforms:\n" +
            "    - !<pseudonymize>\n" +
            "      encoding: \"JSON\"\n" +
            "  date:\n" +
            "    type: \"string\"\n" +
            "    format: \"date\"\n" +
            "  timestamp:\n" +
            "    type: \"string\"\n" +
            "    format: \"date-time\"\n";
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({"$schema", "title"})
    static class JsonSchemaWithTransforms {

        String type;

        //only applicable if type==object
        Boolean additionalProperties;


        //only applicable if type==String
        String format;

        Map<String, JsonSchemaWithTransforms> properties;

        //TODO: this Transform class has stuff not applicable to JsonSchema-based use-case
        // eg `jsonPath`, `fields`, etc.
        List<Transform> transforms;

        public void addTransform(Transform transform) {
            if (transforms == null) {
                transforms = new LinkedList<>();
            }
            transforms.add(transform);
        }
    }
}
