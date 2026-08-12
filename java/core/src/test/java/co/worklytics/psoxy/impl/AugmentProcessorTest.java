package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Warning;
import com.avaulta.gateway.resources.ResourceService;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.augments.GenMetadataProcessor;
import com.avaulta.gateway.rules.augments.SentenceMetadataProcessor;
import com.avaulta.gateway.rules.augments.UnavailableGenMetadataBackend;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AugmentProcessorTest {

    AugmentProcessor augmentProcessor;
    Configuration jsonConfiguration;
    ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        jsonConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

        ResourceService noModels = path -> Optional.empty();
        GenMetadataProcessor genMetadataProcessor =
            new GenMetadataProcessor(new UnavailableGenMetadataBackend(), objectMapper);
        augmentProcessor = new AugmentProcessor(jsonConfiguration,
            new JsonSchemaValidationUtils(objectMapper),
            objectMapper,
            new SentenceMetadataProcessor(noModels),
            genMetadataProcessor);
    }

    @Test
    void extractLeafFieldNameFromConcrete_bracketNotation() {
        assertEquals("content",
            AugmentProcessor.extractLeafFieldNameFromConcrete("$['body']['content']"));
    }

    @Test
    void extractLeafFieldNameFromConcrete_deepPath() {
        assertEquals("content",
            AugmentProcessor.extractLeafFieldNameFromConcrete("$['value'][0]['body']['content']"));
    }

    @Test
    void extractParentFromConcrete_bracketNotation() {
        assertEquals("$['body']",
            AugmentProcessor.extractParentFromConcrete("$['body']['content']"));
    }

    @Test
    void extractParentFromConcrete_withArrayIndex() {
        assertEquals("$['value'][0]['body']",
            AugmentProcessor.extractParentFromConcrete("$['value'][0]['body']['content']"));
    }

    @Test
    void extractParentFromConcrete_rootLevel() {
        assertNull(AugmentProcessor.extractParentFromConcrete("$"));
    }

    @SneakyThrows
    @Test
    void applyAugments_textDigest_addsProperty() {
        Augment.TextDigest augment = Augment.TextDigest.builder()
            .jsonPath("$.body.content")
            .build();

        // Build a document: {"body": {"content": "Hello world this is a test"}}
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", "Hello world this is a test");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", body);

        augmentProcessor.applyAugments(List.of(augment), document);

        // Verify the original value is preserved
        @SuppressWarnings("unchecked")
        Map<String, Object> resultBody = (Map<String, Object>) document.get("body");
        assertEquals("Hello world this is a test", resultBody.get("content"));

        // Verify the augment property was added
        assertTrue(resultBody.containsKey("+content:textDigest"));
        @SuppressWarnings("unchecked")
        Map<String, Object> digest = (Map<String, Object>) resultBody.get("+content:textDigest");
        assertEquals(26, digest.get("length"));
        assertEquals(6, digest.get("word_count"));
    }

    @SneakyThrows
    @Test
    void applyAugments_textDigest_withKeywords() {
        Augment.TextDigest augment = Augment.TextDigest.builder()
            .jsonPath("$.body.content")
            .keywords(Arrays.asList("hello", "test"))
            .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", "Hello world this is a test");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", body);

        augmentProcessor.applyAugments(List.of(augment), document);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultBody = (Map<String, Object>) document.get("body");
        @SuppressWarnings("unchecked")
        Map<String, Object> digest = (Map<String, Object>) resultBody.get("+content:textDigest");

        assertNotNull(digest);
        assertTrue(digest.containsKey("keywords"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> keywords = (Map<String, Integer>) digest.get("keywords");
        assertEquals(1, keywords.get("hello"));
        assertEquals(1, keywords.get("test"));
    }

    @SneakyThrows
    @Test
    void applyAugments_conflictDetection_skipsAllAugments() {
        Augment.TextDigest augment = Augment.TextDigest.builder()
            .jsonPath("$.body.content")
            .build();

        // Document already has a "+" property
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", "Hello world");
        body.put("+content:someExisting", "conflict");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", body);

        augmentProcessor.applyAugments(List.of(augment), document);

        // Verify no new augment property was added
        @SuppressWarnings("unchecked")
        Map<String, Object> resultBody = (Map<String, Object>) document.get("body");
        assertFalse(resultBody.containsKey("+content:textDigest"));
        // But the existing conflicting property is preserved
        assertEquals("conflict", resultBody.get("+content:someExisting"));
    }

    @SneakyThrows
    @Test
    void applyAugments_pathNotPresent_noOp() {
        Augment.TextDigest augment = Augment.TextDigest.builder()
            .jsonPath("$.body.content")
            .build();

        // Document doesn't have body.content
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("name", "test");

        augmentProcessor.applyAugments(List.of(augment), document);

        // No crash, no augment property added
        assertFalse(document.containsKey("+content:textDigest"));
    }

    @SneakyThrows
    @Test
    void applyAugments_nullInput_noOp() {
        Augment.TextDigest augment = Augment.TextDigest.builder()
            .jsonPath("$.body.content")
            .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", null);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", body);

        augmentProcessor.applyAugments(List.of(augment), document);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultBody = (Map<String, Object>) document.get("body");
        // null input → augment computes null → not inserted
        assertFalse(resultBody.containsKey("+content:textDigest"));
    }

    @SneakyThrows
    @Test
    void applyAugments_emptyString_producesDefaultResult() {
        Augment.TextDigest augment = Augment.TextDigest.builder()
            .jsonPath("$.body.content")
            .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", "");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", body);

        augmentProcessor.applyAugments(List.of(augment), document);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultBody = (Map<String, Object>) document.get("body");
        @SuppressWarnings("unchecked")
        Map<String, Object> digest = (Map<String, Object>) resultBody.get("+content:textDigest");
        assertNotNull(digest);
        assertEquals(0, digest.get("length"));
        assertEquals(0, digest.get("word_count"));
    }

    @SneakyThrows
    @Test
    void applyAugments_emptyList_noOp() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", Map.of("content", "Hello"));

        augmentProcessor.applyAugments(List.of(), document);

        // No crash
        assertEquals(1, document.size());
    }

    @SneakyThrows
    @Test
    void applyAugments_computeException_skipsGracefully() {
        // Create an augment that always throws
        Augment failingAugment = new Augment() {
            @Override
            public List<String> getJsonPaths() {
                return List.of("$.body.content");
            }
            @Override
            public String getFunctionName() {
                return "failing";
            }
            @Override
            public Object compute(Object input) {
                throw new RuntimeException("Intentional test failure");
            }
        };

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", "Hello world");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", body);

        // Should not throw
        assertDoesNotThrow(() -> augmentProcessor.applyAugments(List.of(failingAugment), document));

        // No augment property added
        @SuppressWarnings("unchecked")
        Map<String, Object> resultBody = (Map<String, Object>) document.get("body");
        assertFalse(resultBody.containsKey("+content:failing"));
    }

    @Test
    void hasConflictingProperties_noConflicts() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", Map.of("content", "Hello"));
        assertFalse(augmentProcessor.hasConflictingProperties(document));
    }

    @Test
    void hasConflictingProperties_deeplyNested() {
        Map<String, Object> deep = new LinkedHashMap<>();
        deep.put("+existing", "conflict");
        Map<String, Object> level2 = new LinkedHashMap<>();
        level2.put("nested", deep);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("value", List.of(level2));
        assertTrue(augmentProcessor.hasConflictingProperties(document));
    }

    @SneakyThrows
    @Test
    void applyAugments_innerJsonPath_extractsFromEscapedJson() {
        String adaptiveCard = """
            {
              "body": [
                {"type": "TextBlock", "text": "Hello world"},
                {"type": "TextBlock", "text": "Second block"}
              ]
            }
            """;
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("content", adaptiveCard);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("attachments", List.of(attachment));

        Augment.TextDigest augment = Augment.TextDigest.builder()
            .jsonPath("$.attachments[*].content")
            .innerJsonPath("$..text")
            .build();

        augmentProcessor.applyAugments(List.of(augment), document);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultAttachment = (Map<String, Object>)
            ((List<?>) document.get("attachments")).get(0);
        assertFalse(resultAttachment.containsKey("+content.body[0].text:textDigest"));
        @SuppressWarnings("unchecked")
        Map<String, Object> innerAugments = (Map<String, Object>) resultAttachment.get("+content:textDigest");
        assertNotNull(innerAugments);
        assertEquals(2, innerAugments.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> digest0 = (Map<String, Object>) innerAugments.get("body[0].text");
        @SuppressWarnings("unchecked")
        Map<String, Object> digest1 = (Map<String, Object>) innerAugments.get("body[1].text");
        assertNotNull(digest0);
        assertNotNull(digest1);
        assertEquals(11, digest0.get("length"));
        assertEquals(2, digest0.get("word_count"));
        assertEquals(12, digest1.get("length"));
        assertEquals(2, digest1.get("word_count"));
    }

    @Test
    void toInnerPathSuffix_bracketNotation() {
        assertEquals("body[0].text",
            AugmentProcessor.toInnerPathSuffix("$['body'][0]['text']"));
    }

    @Test
    void buildAugmentPropertyName() {
        assertEquals("+content:textDigest",
            AugmentProcessor.buildAugmentPropertyName("content", "textDigest"));
    }

    @Test
    void hasConflictingProperties_topLevel() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("+augmented", "conflict");
        assertTrue(augmentProcessor.hasConflictingProperties(document));
    }

    @Test
    void hasConflictingProperties_nested() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("+existing", "conflict");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", nested);
        assertTrue(augmentProcessor.hasConflictingProperties(document));
    }

    @Test
    void hasConflictingProperties_inArray() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("+existing", "conflict");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("value", List.of(item));
        assertTrue(augmentProcessor.hasConflictingProperties(document));
    }

    @Test
    void augmentPropertyName_usesConstants() {
        // Verify the naming convention uses the defined constants
        String expected = AugmentProcessor.AUGMENT_PROPERTY_PREFIX + "content"
            + AugmentProcessor.AUGMENT_SEPARATOR + "textDigest";
        assertEquals("+content:textDigest", expected);
    }

    @SneakyThrows
    @Test
    void applyAugments_outputSchemaMismatch_omitsPropertyAndWarns() {
        Augment.TextDigest augment = Augment.TextDigest.builder()
            .jsonPath("$.body.content")
            .outputSchema(JsonSchemaFilter.builder()
                .type("object")
                .required(List.of("category"))
                .properties(Map.of(
                    "category", JsonSchemaFilter.builder().type("string").build()))
                .build())
            .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", "Hello world");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", body);

        List<String> warnings = augmentProcessor.applyAugments(List.of(augment), document);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultBody = (Map<String, Object>) document.get("body");
        assertFalse(resultBody.containsKey("+content:textDigest"));
        assertTrue(warnings.contains(Warning.AUGMENT_OUTPUT_SCHEMA_MISMATCH.asHttpHeaderCode()));
    }

    @Test
    void applyAugments_conflictDetection_reportsWarning() {
        Augment.TextDigest augment = Augment.TextDigest.builder()
            .jsonPath("$.body.content")
            .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", "Hello world");
        body.put("+content:someExisting", "conflict");
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("body", body);

        List<String> warnings = augmentProcessor.applyAugments(List.of(augment), document);

        assertTrue(warnings.contains(Warning.AUGMENT_CONFLICT_SKIPPED.asHttpHeaderCode()));
    }
}
