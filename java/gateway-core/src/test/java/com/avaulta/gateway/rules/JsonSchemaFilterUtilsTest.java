package com.avaulta.gateway.rules;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaFilterUtilsTest {

    JsonSchemaFilterUtils jsonSchemaFilterUtils;
    ObjectMapper objectMapper;
    ObjectMapper yamlMapper;

    ObjectReader schemaReader;


    @BeforeEach
    void setup() {
        jsonSchemaFilterUtils = new JsonSchemaFilterUtils();

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false); //want ISO-strings

        yamlMapper = new YAMLMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        jsonSchemaFilterUtils.objectMapper = objectMapper;
        schemaReader = objectMapper.readerFor(JsonSchemaFilter.class);
    }

    @SneakyThrows
    @Test
    void filterBySchema() {

        SimplePojoPlus simplePlus = SimplePojoPlus.builder()
                .someString("some-string")
                .date(LocalDate.parse("2023-01-16"))
                .timestamp(Instant.parse("2023-01-16T05:12:34Z"))
                .timestampWithMillis(Instant.parse("2023-01-16T05:12:34.123Z"))
                .someListItem("list-item-1")
                .build();

        Pair<Object, List<String>> filteredToSimplePlus =
                jsonSchemaFilterUtils.filterObjectBySchema(simplePlus,
                        schemaReader.readValue(SimplePojoPlus.EXPECTED_SCHEMA));


        assertEquals("{\n" +
                        "  \"someString\" : \"some-string\",\n" +
                        "  \"date\" : \"2023-01-16\",\n" +
                        "  \"timestamp\" : \"2023-01-16T05:12:34Z\",\n" +
                        "  \"timestampWithMillis\" : \"2023-01-16T05:12:34.123Z\",\n" +
                        "  \"someListItems\" : [ \"list-item-1\" ]\n" +
                        "}",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filteredToSimplePlus.getLeft()));


        Pair<Object, List<String>> filteredToSimple =
                jsonSchemaFilterUtils.filterObjectBySchema(simplePlus,
                        schemaReader.readValue(SimplePojo.EXPECTED_SCHEMA));

        assertEquals("{\n" +
                        "  \"someString\" : \"some-string\",\n" +
                        "  \"date\" : \"2023-01-16\",\n" +
                        "  \"timestamp\" : \"2023-01-16T05:12:34Z\",\n" +
                        "  \"timestampWithMillis\" : \"2023-01-16T05:12:34.123Z\"\n" +
                        "}",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filteredToSimple.getLeft()));

        assertEquals(1, filteredToSimple.getRight().size());
        assertEquals("$.someListItems", filteredToSimple.getRight().get(0));
    }

    @Builder
    @Data
    static class SimplePojoPlus {

        static final String EXPECTED_SCHEMA = "{\n" +
                "  \"type\" : \"object\",\n" +
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
                "    },\n" +
                "    \"timestampWithMillis\" : {\n" +
                "      \"type\" : \"string\",\n" +
                "      \"format\" : \"date-time\"\n" +
                "    },\n" +
                "    \"someListItems\" : {\n" +
                "      \"type\" : \"array\",\n" +
                "      \"items\" : {\n" +
                "        \"type\" : \"string\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        String someString;

        LocalDate date;

        Instant timestamp;

        Instant timestampWithMillis;

        @Singular
        List<String> someListItems;
    }


    @Data
    static class SimplePojo {

        String someString;

        LocalDate date;

        Instant timestamp;

        static final String EXPECTED_SCHEMA = "{\n" +
                "  \"type\" : \"object\",\n" +
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
                "    },\n" +
                "    \"timestampWithMillis\" : {\n" +
                "      \"type\" : \"string\",\n" +
                "      \"format\" : \"date-time\"\n" +
                "    }\n" +
                "  }\n" +
                "}";

        static final String EXPECTED_SCHEMA_YAML = "---\n" +
                "type: \"object\"\n" +
                "properties:\n" +
                "  someString:\n" +
                "    type: \"string\"\n" +
                "  date:\n" +
                "    type: \"string\"\n" +
                "    format: \"date\"\n" +
                "  timestamp:\n" +
                "    type: \"string\"\n" +
                "    format: \"date-time\"\n" +
                "  timestampWithMillis:\n" +
                "    type: \"string\"\n" +
                "    format: \"date-time\"\n";

    }

    @SneakyThrows
    @Test
    void filterBySchema_refs() {

        JsonSchemaFilter schemaWithRefs =
                schemaReader.readValue(ComplexPojo.EXPECTED_SCHEMA);

        SimplePojoPlus simplePlus = SimplePojoPlus.builder()
                .someString("some-string")
                .date(LocalDate.parse("2023-01-16"))
                .timestamp(Instant.parse("2023-01-16T05:12:34Z"))
                .someListItem("list-item-1")
                .build();


        assertEquals(ComplexPojo.EXPECTED_SCHEMA,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schemaWithRefs));

        assertEquals("{\n" +
                    "  \"simplePojo\" : {\n" +
                    "    \"someString\" : \"some-string\",\n" +
                    "    \"date\" : \"2023-01-16\",\n" +
                    "    \"timestamp\" : \"2023-01-16T05:12:34Z\"\n" +
                    "  },\n" +
                    "  \"additionalSimplePojos\" : [ {\n" +
                    "    \"someString\" : \"some-string\",\n" +
                    "    \"date\" : \"2023-01-16\",\n" +
                    "    \"timestamp\" : \"2023-01-16T05:12:34Z\"\n" +
                    "  } ]\n" +
                    "}",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchemaFilterUtils.filterObjectBySchema(ComplexPojoPlus.builder()
                        .simplePojo(simplePlus)
                        .additionalSimplePojo(simplePlus)
                        .build(), schemaWithRefs).getLeft()));

    }


    @Builder
    @Data
    static class ComplexPojo {

        public static final String EXPECTED_SCHEMA = "{\n" +
            "  \"type\" : \"object\",\n" +
            "  \"properties\" : {\n" +
            "    \"additionalSimplePojos\" : {\n" +
            "      \"type\" : \"array\",\n" +
            "      \"items\" : {\n" +
            "        \"$ref\" : \"#/definitions/SimplePojo\"\n" +
            "      }\n" +
            "    },\n" +
            "    \"simplePojo\" : {\n" +
            "      \"$ref\" : \"#/definitions/SimplePojo\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"definitions\" : {\n" +
            "    \"SimplePojo\" : {\n" +
            "      \"type\" : \"object\",\n" +
            "      \"properties\" : {\n" +
            "        \"date\" : {\n" +
            "          \"type\" : \"string\",\n" +
            "          \"format\" : \"date\"\n" +
            "        },\n" +
            "        \"someString\" : {\n" +
            "          \"type\" : \"string\"\n" +
            "        },\n" +
            "        \"timestamp\" : {\n" +
            "          \"type\" : \"string\",\n" +
            "          \"format\" : \"date-time\"\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
        public static final String EXPECTED_COMPACT_SCHEMA = "{\n" +
                "  \"properties\" : {\n" +
                "    \"simplePojo\" : {\n" +
                "      \"$ref\" : \"#/definitions/SimplePojo\"\n" +
                "    },\n" +
                "    \"additionalSimplePojos\" : {\n" +
                "      \"items\" : {\n" +
                "        \"$ref\" : \"#/definitions/SimplePojo\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"definitions\" : {\n" +
                "    \"SimplePojo\" : {\n" +
                "      \"properties\" : {\n" +
                "        \"someString\" : { },\n" +
                "        \"date\" : {\n" +
                "          \"format\" : \"date\"\n" +
                "        },\n" +
                "        \"timestamp\" : {\n" +
                "          \"format\" : \"date-time\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";


        SimplePojo simplePojo;

        List<SimplePojo> additionalSimplePojos;
    }

    @Builder
    @Data
    static class ComplexPojoPlus {

        SimplePojoPlus simplePojo;

        @Singular
        List<SimplePojoPlus> additionalSimplePojos;
    }

}
