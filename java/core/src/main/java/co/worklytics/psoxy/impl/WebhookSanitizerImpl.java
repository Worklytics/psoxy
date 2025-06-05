package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.WebhookSanitizer;
import co.worklytics.psoxy.utils.email.EmailAddressParser;
import com.avaulta.gateway.rules.WebhookCollectionRules;
import com.avaulta.gateway.rules.transforms.Transform;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import org.apache.commons.lang3.ObjectUtils;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebhookSanitizerImpl implements WebhookSanitizer {


    @Inject
    Pseudonymizer pseudonymizer;
    @Inject
    EmailAddressParser emailAddressParser;
    @Inject
    SanitizerUtils sanitizerUtils;
    @Inject
    Configuration jsonConfiguration;

    final WebhookCollectionRules webhookRules;

    Map<Transform, List<JsonPath>> compiledTransforms = new ConcurrentHashMap<>();

    @AssistedInject
    public WebhookSanitizerImpl(@Assisted WebhookCollectionRules webhookCollectionRules) {
        this.webhookRules = webhookCollectionRules;
    }


    @Override
    public boolean canAccept(HttpEventRequest request) {

        // for foreseeable future, we only accept JSON
        // for simplicity, if content type is not specified, we assume it is json
        boolean acceptableContentType = request.getHeader("Content-Type")
            .map(s -> s.startsWith("application/json"))
            .orElse(true);

        // in theory, json can be encoded in any unicode charset, but in practice, we only support UTF-8
        // so just check if a charset OTHER than UTF-8 is specified; and if so we'll reject
        boolean explicitCharsetOtherThanUtf8 = request.getHeader("Content-Type")
            .map(s -> s.contains("charset=") && !s.toLowerCase().contains("charset=utf-8"))
            .orElse(false);

        //TODO: check path templates
        return acceptableContentType && !explicitCharsetOtherThanUtf8;
    }

    @Override
    public boolean verifyClaims(HttpEventRequest request, Map<String, String> claims) {
        return true;
    }

    @Override
    public ProcessedContent sanitize(HttpEventRequest request) {

        if (!canAccept(request)) {
            throw new IllegalArgumentException("Request not accepted by this sanitizer");
        }

        if (webhookRules.getEndpoints().size() != 1) {
            throw new IllegalArgumentException("Expected exactly one endpoint in webhook rules, found: " + webhookRules.getEndpoints().size());
        }

        // per above, we assume there is exactly one endpoint configured atm
        WebhookCollectionRules.WebhookEndpoint endpoint = webhookRules.getEndpoints().get(0);

        String sanitizedContent = new String(request.getBody(), StandardCharsets.UTF_8); //just assume UTF-8, always
        if (ObjectUtils.isNotEmpty(endpoint.getTransforms())) {
            Object document = jsonConfiguration.jsonProvider().parse(request.getBody());
            for (Transform transform : endpoint.getTransforms()) {
                sanitizerUtils.applyTransform(pseudonymizer, transform, document, compiledTransforms);
            }
            sanitizedContent = jsonConfiguration.jsonProvider().toJson(document);
        }

        return ProcessedContent.builder()
                .content(sanitizedContent.getBytes(StandardCharsets.UTF_8))
                .contentType(request.getHeader("Content-Type").orElse("application/json"))
                .build();
    }
}
