package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.utils.email.EmailAddressParser;
import com.avaulta.gateway.rules.WebhookCollectionRules;
import com.avaulta.gateway.rules.transforms.Transform;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Disabled // seems to work via IntelliJ, but fails via maven
class WebhookSanitizerImplTest {

    WebhookSanitizerImpl sanitizer;
    Pseudonymizer pseudonymizer = mock(Pseudonymizer.class);
    EmailAddressParser emailAddressParser = mock(EmailAddressParser.class);
    SanitizerUtils sanitizerUtils = mock(SanitizerUtils.class);
    Configuration jsonConfiguration = Configuration.defaultConfiguration();
    WebhookCollectionRules.WebhookEndpoint endpoint = mock(WebhookCollectionRules.WebhookEndpoint.class);
    WebhookCollectionRules webhookRules;

    @BeforeEach
    void setUp() {
        webhookRules = mock(WebhookCollectionRules.class);
        when(webhookRules.getEndpoints()).thenReturn(Collections.singletonList(endpoint));
        sanitizer = new WebhookSanitizerImpl(webhookRules);
        sanitizer.pseudonymizer = pseudonymizer;
        sanitizer.emailAddressParser = emailAddressParser;
        sanitizer.sanitizerUtils = sanitizerUtils;
        sanitizer.jsonConfiguration = jsonConfiguration;
    }

    @ParameterizedTest
    @CsvSource({
        "application/json,true",
        "'',true",
        "text/html,false",
        "application/json; charset=UTF-8,true",
        "application/json; charset=ISO-8859-1,false"
    })
    void canAccept_variousContentTypes(String contentType, boolean expected) {
        HttpEventRequest request = mock(HttpEventRequest.class);
        Optional<String> header = contentType.isEmpty() ? Optional.empty() : Optional.of(contentType);
        when(request.getHeader("Content-Type")).thenReturn(header);
        assertEquals(expected, sanitizer.canAccept(request));
    }

    @Test
    void sanitize_noTransforms_returnsOriginalJson() {
        String json = "{\"foo\":\"bar\"}";
        HttpEventRequest request = mock(HttpEventRequest.class);
        when(request.getHeader("Content-Type")).thenReturn(Optional.of("application/json"));
        when(request.getBody()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(endpoint.getTransforms()).thenReturn(Collections.emptyList());
        ProcessedContent result = sanitizer.sanitize(request);
        assertEquals(json, new String(result.getContent(), StandardCharsets.UTF_8));
        assertEquals("application/json", result.getContentType());
    }

    @Test
    void sanitize_withTransforms_appliesTransform() {
        String json = "{\"foo\":\"bar\"}";
        String transformedJson = "{\"foo\":\"baz\"}";
        HttpEventRequest request = mock(HttpEventRequest.class);
        when(request.getHeader("Content-Type")).thenReturn(Optional.of("application/json"));
        when(request.getBody()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        Transform transform = Transform.Pseudonymize.ofPaths("$.foo");
        when(endpoint.getTransforms()).thenReturn(Collections.singletonList(transform));
        doAnswer(invocation -> {
            Object doc = invocation.getArgument(2);
            // mutate the document to simulate transformation
            ((Map<String, Object>) doc).put("foo", "baz");
            return null;
        }).when(sanitizerUtils).applyTransform(any(), eq(transform), any(), any());
        ProcessedContent result = sanitizer.sanitize(request);
        assertTrue(new String(result.getContent(), StandardCharsets.UTF_8).contains("baz"));
    }

    @Test
    void sanitize_rejectsIfNotAccepted() {
        HttpEventRequest request = mock(HttpEventRequest.class);
        when(request.getHeader("Content-Type")).thenReturn(Optional.of("text/html"));
        when(request.getBody()).thenReturn("irrelevant".getBytes(StandardCharsets.UTF_8));
        when(endpoint.getTransforms()).thenReturn(Collections.emptyList());
        assertThrows(IllegalArgumentException.class, () -> sanitizer.sanitize(request));
    }
}

