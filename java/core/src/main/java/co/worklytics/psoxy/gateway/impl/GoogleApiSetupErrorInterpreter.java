package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ErrorCauses;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Value;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Classifies common Google API / OAuth setup failures so the proxy can return a specific
 * {@link ErrorCauses} and a sanitized response body (without project IDs, activation URLs, etc.).
 */
@Log
public final class GoogleApiSetupErrorInterpreter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value
    @Builder
    public static class InterpretedSetupError {
        ErrorCauses errorCause;
        String clientResponseBody;
    }

    private GoogleApiSetupErrorInterpreter() {}

    public static Optional<InterpretedSetupError> interpretSourceApiError(int statusCode, String responseBody) {
        if (statusCode != 403 || StringUtils.isBlank(responseBody)) {
            return Optional.empty();
        }
        try {
            JsonNode error = MAPPER.readTree(responseBody).get("error");
            if (error == null || !isApiNotEnabledError(error)) {
                return Optional.empty();
            }
            return Optional.of(apiNotEnabledResponse(error));
        } catch (Exception e) {
            log.fine("Failed to parse source API error JSON: " + e.getMessage());
            if (looksLikeApiNotEnabled(responseBody)) {
                return Optional.of(InterpretedSetupError.builder()
                    .errorCause(ErrorCauses.SOURCE_API_NOT_ENABLED)
                    .clientResponseBody(apiNotEnabledJson(null, null))
                    .build());
            }
            return Optional.empty();
        }
    }

    public static Optional<InterpretedSetupError> interpretTokenExchangeFailure(String message) {
        if (StringUtils.isBlank(message) || !message.contains("oauth2.googleapis.com/token")) {
            return Optional.empty();
        }
        if (message.contains("access_denied")) {
            return Optional.of(tokenError(
                ErrorCauses.SOURCE_OAUTH_SCOPE_MISMATCH,
                "OAuth scopes requested by the proxy are not granted via Domain-wide Delegation"));
        }
        if (message.contains("invalid_scope")) {
            return Optional.of(tokenError(
                ErrorCauses.SOURCE_OAUTH_SCOPE_MISMATCH,
                "OAuth scopes configured on the proxy are invalid or malformed"));
        }
        if (message.contains("invalid_grant")
            && (message.contains("Invalid JWT Signature") || message.contains("SignatureException"))) {
            return Optional.of(tokenError(
                ErrorCauses.SOURCE_CREDENTIALS_INVALID,
                "Service account key is invalid, revoked, or does not match the provisioned account"));
        }
        if (message.contains("401 Unauthorized") || message.contains("unauthorized_client")) {
            return Optional.of(tokenError(
                ErrorCauses.SOURCE_DWD_NOT_GRANTED,
                "Domain-wide Delegation has not been granted for this service account"));
        }
        return Optional.empty();
    }

    private static InterpretedSetupError apiNotEnabledResponse(JsonNode error) {
        String service = extractMetadataField(error, "service");
        String serviceTitle = extractMetadataField(error, "serviceTitle");
        return InterpretedSetupError.builder()
            .errorCause(ErrorCauses.SOURCE_API_NOT_ENABLED)
            .clientResponseBody(apiNotEnabledJson(service, serviceTitle))
            .build();
    }

    private static InterpretedSetupError tokenError(ErrorCauses cause, String message) {
        return InterpretedSetupError.builder()
            .errorCause(cause)
            .clientResponseBody("{\"message\":\"" + escapeJson(message) + "\"}")
            .build();
    }

    private static String apiNotEnabledJson(String service, String serviceTitle) {
        StringBuilder body = new StringBuilder(
            "{\"message\":\"GCP API is not enabled for the OAuth client project\"");
        if (StringUtils.isNotBlank(service)) {
            body.append(",\"api\":\"").append(escapeJson(service)).append("\"");
        }
        if (StringUtils.isNotBlank(serviceTitle)) {
            body.append(",\"apiTitle\":\"").append(escapeJson(serviceTitle)).append("\"");
        }
        body.append("}");
        return body.toString();
    }

    private static boolean isApiNotEnabledError(JsonNode error) {
        if (hasNestedReason(error.path("errors"), "accessNotConfigured")) {
            return true;
        }
        if (hasNestedReason(error.path("details"), "SERVICE_DISABLED")) {
            return true;
        }
        return looksLikeApiNotEnabled(error.path("message").asText(""));
    }

    private static boolean looksLikeApiNotEnabled(String text) {
        return StringUtils.isNotBlank(text)
            && (text.contains("accessNotConfigured")
                || text.contains("SERVICE_DISABLED")
                || (text.contains("has not been used in project") && text.contains("disabled")));
    }

    private static boolean hasNestedReason(JsonNode arrayNode, String reason) {
        if (!arrayNode.isArray()) {
            return false;
        }
        return StreamSupport.stream(arrayNode.spliterator(), false)
            .anyMatch(node -> reason.equals(node.path("reason").asText(null)));
    }

    private static String extractMetadataField(JsonNode error, String fieldName) {
        JsonNode details = error.path("details");
        if (!details.isArray()) {
            return null;
        }
        for (JsonNode detail : details) {
            JsonNode metadata = detail.path("metadata");
            if (metadata.has(fieldName)) {
                return metadata.path(fieldName).asText(null);
            }
        }
        return null;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
