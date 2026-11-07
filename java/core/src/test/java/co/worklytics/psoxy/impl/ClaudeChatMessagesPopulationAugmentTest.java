package co.worklytics.psoxy.impl;

import com.avaulta.gateway.rules.JsonSchema;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.augments.AugmentValidation;
import com.avaulta.gateway.rules.augments.ClassifyProcessor;
import com.avaulta.gateway.rules.augments.GenMetadataInferenceResult;
import com.avaulta.gateway.rules.augments.GenMetadataProcessor;
import com.avaulta.gateway.rules.augments.TextMetadataProcessor;
import com.avaulta.gateway.rules.augments.UnavailableGenMetadataBackend;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PoC: conditional {@code classify} augment on Claude Compliance {@code .../chats/{id}/messages}
 * responses — LLM runs only when {@code user.id} matches {@code applyOnlyWhen}.
 */
class ClaudeChatMessagesPopulationAugmentTest {

    private static final String CLASSIFY_AUGMENT_YAML =
        "alpha-features/claude-population/chat-messages-classify-by-user.yaml";
    private static final String TARGET_USER_RESPONSE =
        "alpha-features/claude-population/chat-messages-user-xyz456.json";
    private static final String OTHER_USER_RESPONSE =
        "alpha-features/claude-population/chat-messages-user-other.json";

    ObjectMapper objectMapper;
    ObjectMapper yamlMapper;
    AtomicInteger inferenceCalls;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        yamlMapper = new ObjectMapper(new YAMLFactory());
        inferenceCalls = new AtomicInteger();
    }

    @Test
    void exampleYaml_deserializesApplyOnlyWhen() throws Exception {
        Augment.Classify augment = loadClassifyAugment();
        assertEquals("classify", augment.getFunctionName());
        assertEquals(List.of("$"), augment.getJsonPaths());
        assertEquals("$[?(@.user.id == 'user_xyz456')]", augment.getApplyOnlyWhen());
        AugmentValidation.validateAugments(List.of(augment));
    }

    @Test
    void targetUserId_runsClassify_otherUser_skipped() throws Exception {
        Augment.Classify augment = loadClassifyAugment();
        AugmentProcessor processor = processorCountingInferences("Requirements & Planning");

        Map<String, Object> targetChat = loadJson(TARGET_USER_RESPONSE);
        List<String> targetWarnings = processor.applyAugments(List.of(augment), targetChat);

        assertTrue(targetWarnings.isEmpty());
        assertEquals("Requirements & Planning", targetChat.get("+self:classify"));
        assertEquals(1, inferenceCalls.get());

        Map<String, Object> otherChat = loadJson(OTHER_USER_RESPONSE);
        processor.applyAugments(List.of(augment), otherChat);

        assertNull(otherChat.get("+self:classify"));
        assertEquals(1, inferenceCalls.get());
        assertFalse(otherChat.isEmpty());
    }

    @Test
    void multiUserInFilter_runsForAnyListedId() throws Exception {
        Augment.Classify augment = Augment.Classify.builder()
            .jsonPath("$")
            .applyOnlyWhen(
                "$[?(@.user.id in ['user_xyz456','user_abc111'])]")
            .prompt("Classify")
            .classes(List.of("Requirements & Planning", "Uncategorized"))
            .build();
        AugmentProcessor processor = processorCountingInferences("Requirements & Planning");

        Map<String, Object> secondPilot = loadJson(OTHER_USER_RESPONSE);
        secondPilot.put("user", Map.of("id", "user_abc111", "email_address", "b@example.com"));

        processor.applyAugments(List.of(augment), secondPilot);

        assertEquals("Requirements & Planning", secondPilot.get("+self:classify"));
        assertEquals(1, inferenceCalls.get());
    }

    @Test
    void genMetadata_applyOnlyWhen_sameGateAsClassify() throws Exception {
        JsonSchema schema = JsonSchema.builder()
            .type("object")
            .required(List.of("theme"))
            .additionalProperties(false)
            .properties(Map.of("theme", JsonSchema.builder().type("string").build()))
            .build();
        Augment.GenMetadata augment = Augment.GenMetadata.builder()
            .jsonPath("$")
            .applyOnlyWhen("$[?(@.user.id == 'user_xyz456')]")
            .prompt("Extract a one-word theme")
            .outputSchema(schema)
            .maxOutputTokens(50)
            .build();

        AtomicInteger genCalls = new AtomicInteger();
        GenMetadataProcessor genMetadataProcessor = new GenMetadataProcessor(
            (taskPrompt, outputSchema, inputData, options) -> {
                genCalls.incrementAndGet();
                return GenMetadataInferenceResult.of("{\"theme\":\"dashboard\"}");
            },
            objectMapper,
            2,
            new JsonSchemaValidationUtils());
        Configuration jsonConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();
        AugmentProcessor processor = new AugmentProcessor(jsonConfiguration,
            new JsonSchemaValidationUtils(),
            objectMapper,
            new TextMetadataProcessor(path -> Optional.empty()),
            genMetadataProcessor,
            new ClassifyProcessor(new UnavailableGenMetadataBackend(), objectMapper, 2));

        Map<String, Object> targetChat = loadJson(TARGET_USER_RESPONSE);
        Map<String, Object> otherChat = loadJson(OTHER_USER_RESPONSE);

        processor.applyAugments(List.of(augment), targetChat);
        processor.applyAugments(List.of(augment), otherChat);

        assertEquals(1, genCalls.get());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) targetChat.get("+self:genMetadata");
        assertEquals("dashboard", meta.get("theme"));
        assertFalse(otherChat.containsKey("+self:genMetadata"));
    }

    @Test
    void applyOnlyWhen_noMatch_textDigestAlsoSkipped() {
        Augment.TextDigest augment = Augment.TextDigest.builder()
            .jsonPath("$.chat_messages[*].content[*].text")
            .applyOnlyWhen("$[?(@.user.id == 'user_xyz456')]")
            .build();

        Configuration jsonConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();
        AugmentProcessor processor = new AugmentProcessor(jsonConfiguration,
            new JsonSchemaValidationUtils(),
            objectMapper,
            new TextMetadataProcessor(path -> Optional.empty()),
            new GenMetadataProcessor(new UnavailableGenMetadataBackend(), objectMapper, 2,
                new JsonSchemaValidationUtils()),
            new ClassifyProcessor(new UnavailableGenMetadataBackend(), objectMapper, 2));

        Map<String, Object> otherChat = new LinkedHashMap<>();
        otherChat.put("user", Map.of("id", "user_other789"));
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", List.of(Map.of("type", "text", "text", "hello")));
        otherChat.put("chat_messages", List.of(message));

        processor.applyAugments(List.of(augment), otherChat);

        assertFalse(message.containsKey("+text:textDigest"));
    }

    private AugmentProcessor processorCountingInferences(String label) {
        Configuration jsonConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();
        ClassifyProcessor classifyProcessor = new ClassifyProcessor(
            (taskPrompt, outputSchema, inputData, options) -> {
                inferenceCalls.incrementAndGet();
                return GenMetadataInferenceResult.of(label);
            },
            objectMapper,
            2);
        return new AugmentProcessor(jsonConfiguration,
            new JsonSchemaValidationUtils(),
            objectMapper,
            new TextMetadataProcessor(path -> Optional.empty()),
            new GenMetadataProcessor(new UnavailableGenMetadataBackend(), objectMapper, 2,
                new JsonSchemaValidationUtils()),
            classifyProcessor);
    }

    private Augment.Classify loadClassifyAugment() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CLASSIFY_AUGMENT_YAML)) {
            assertNotNull(in, "Missing test resource: " + CLASSIFY_AUGMENT_YAML);
            Augment augment = yamlMapper.readValue(in, Augment.class);
            assertInstanceOf(Augment.Classify.class, augment);
            return (Augment.Classify) augment;
        }
    }

    private Map<String, Object> loadJson(String resourcePath) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Missing test resource: " + resourcePath);
            return objectMapper.readValue(in, new TypeReference<>() {});
        }
    }
}
