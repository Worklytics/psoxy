package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventRequestDto;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.utils.email.EmailAddressParser;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.avaulta.gateway.rules.WebhookCollectionRules;
import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.transforms.Transform;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WebhookSanitizerAugmentsTest {

    WebhookSanitizerImpl sanitizer;
    SanitizerUtils sanitizerUtils;
    Configuration jsonConfiguration;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jsonConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();
        sanitizerUtils = new SanitizerUtils(
            jsonConfiguration,
            mock(ReversibleTokenizationStrategy.class),
            mock(UrlSafeTokenPseudonymEncoder.class),
            mock(EmailAddressParser.class),
            mock(ReversibleTokenizationStrategy.class),
            mock(DeterministicTokenizationStrategy.class));

        WebhookCollectionRules.WebhookEndpoint endpoint = WebhookCollectionRules.WebhookEndpoint.builder()
            .augment(Augment.TextDigest.builder().jsonPath("$.content").build())
            .transform(Transform.Redact.ofPaths("$.content"))
            .build();
        WebhookCollectionRules webhookRules = WebhookCollectionRules.builder()
            .endpoint(endpoint)
            .build();

        sanitizer = new WebhookSanitizerImpl(webhookRules);
        sanitizer.pseudonymizer = mock(Pseudonymizer.class);
        sanitizer.emailAddressParser = mock(EmailAddressParser.class);
        sanitizer.sanitizerUtils = sanitizerUtils;
        sanitizer.jsonConfiguration = jsonConfiguration;
        com.fasterxml.jackson.databind.ObjectMapper om = objectMapper;
        sanitizer.augmentProcessor = new AugmentProcessor(jsonConfiguration,
            new JsonSchemaValidationUtils(om),
            om,
            new com.avaulta.gateway.rules.augments.SentenceMetadataProcessor(path -> java.util.Optional.empty()),
            new com.avaulta.gateway.rules.augments.GenMetadataProcessor(
                new com.avaulta.gateway.rules.augments.UnavailableGenMetadataBackend(), om));
    }

    @Test
    void sanitize_appliesAugmentsBeforeTransforms() {
        String json = "{\"content\":\"hello world test\"}";
        HttpEventRequest request = HttpEventRequestDto.builder()
            .headers(Map.of("Content-Type", List.of("application/json")))
            .body(json.getBytes(StandardCharsets.UTF_8))
            .build();

        ProcessedContent result = sanitizer.sanitize(request);
        String output = new String(result.getContent(), StandardCharsets.UTF_8);

        assertTrue(output.contains("\"+content:textDigest\""));
        assertTrue(output.contains("\"word_count\""));
        assertFalse(output.contains("\"content\":\"hello world test\""));
    }
}
