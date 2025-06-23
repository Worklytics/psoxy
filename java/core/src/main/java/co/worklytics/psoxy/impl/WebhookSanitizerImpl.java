package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.WebhookSanitizer;
import co.worklytics.psoxy.utils.email.EmailAddressParser;
import com.avaulta.gateway.rules.WebhookCollectionRules;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.annotations.VisibleForTesting;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.extern.java.Log;
import org.apache.commons.lang3.ObjectUtils;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Log
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
    public boolean verifyClaims(HttpEventRequest request, Map<String, Object> claims) {

        if (!canAccept(request)) {
            throw new IllegalArgumentException("Request not accepted by this sanitizer");
        }

        if (webhookRules.getEndpoints().size() != 1) {
            throw new IllegalArgumentException("Expected exactly one endpoint in webhook rules, found: " + webhookRules.getEndpoints().size());
        }

        // per above, we assume there is exactly one endpoint configured atm
        WebhookCollectionRules.WebhookEndpoint endpoint = webhookRules.getEndpoints().get(0);

        return this.verifyClaims(request, claims, endpoint);
    }

    @VisibleForTesting
     boolean verifyClaims(HttpEventRequest request, Map<String, Object> claims, WebhookCollectionRules.WebhookEndpoint endpoint) {

        if (ObjectUtils.isEmpty(endpoint.getJwtClaimsToVerify())) {
            // no claims to verify, so just return true
            return true;
        }

        Object document = null;

        for (String claimToVerify : endpoint.getJwtClaimsToVerify().keySet()) {
            if (!claims.containsKey(claimToVerify)) {
                throw new IllegalArgumentException("Claim " + claimToVerify + " is missing");
            }
            Object value = claims.get(claimToVerify);

            WebhookCollectionRules.JwtClaimSpec spec = endpoint.getJwtClaimsToVerify().get(claimToVerify);

            if (!spec.getPayloadContents().isEmpty() && document == null) {
                // parse the request body as JSON, if we haven't done so already
                document = jsonConfiguration.jsonProvider().parse(request.getBody());
            }

            Object finalDocument = document;
            Optional<String> firstInvalidPath = spec.getPayloadContents().stream()
                .filter(jsonPath -> {
                    // compile the JSONPath if not already compiled
                    JsonPath compiled = JsonPath.compile(jsonPath);

                    try {
                        String payloadValue = compiled.read(finalDocument, jsonConfiguration);

                        if (payloadValue == null) {
                            // consider OK
                            // eg, claimsToVerify only checks the value IFF it's present in the payload
                            return false;
                        }
                        return !payloadValue.equals(value);
                    } catch (PathNotFoundException e) {
                        // consider OK; claimsToVerify only checks value if present in the payload
                        return false;
                    }
                })
                .findFirst();

            if (firstInvalidPath.isPresent()) {
                log.warning(
                    "Claim " + claimToVerify + " with value " + value + " does not match payload contents at path: "
                        + firstInvalidPath.get());
                return false;
            }

            if (spec.getQueryParam() != null) {
                Optional<String> queryParamValue = request.getQuery()
                    .map(query -> {
                        String[] params = query.split("&");
                        for (String param : params) {
                            String[] keyValue = param.split("=");
                            if (keyValue.length == 2 && keyValue[0].equals(spec.getQueryParam())) {
                                return keyValue[1];
                            }
                        }
                        return null;
                    });
                if (queryParamValue.isPresent()) {
                    if (!queryParamValue.get().equals(value)) {
                        log.warning("Claim " + claimToVerify + " with value " + value + " does not match query param "
                            + spec.getQueryParam() + " with value " + queryParamValue.get());
                        return false;
                    }
                }
            }
        }
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
