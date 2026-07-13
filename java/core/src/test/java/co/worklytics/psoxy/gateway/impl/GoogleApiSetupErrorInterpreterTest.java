package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ErrorCauses;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GoogleApiSetupErrorInterpreterTest {

    private static final String API_NOT_ENABLED_BODY = """
        {
          "error": {
            "code": 403,
            "message": "Admin SDK API has not been used in project 123456789012 before or it is disabled. Enable it by visiting https://console.developers.google.com/apis/api/admin.googleapis.com/overview?project=123456789012 then retry.",
            "errors": [
              {
                "message": "Admin SDK API has not been used in project 123456789012 before or it is disabled.",
                "domain": "usageLimits",
                "reason": "accessNotConfigured"
              }
            ],
            "status": "PERMISSION_DENIED",
            "details": [
              {
                "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                "reason": "SERVICE_DISABLED",
                "domain": "googleapis.com",
                "metadata": {
                  "service": "admin.googleapis.com",
                  "serviceTitle": "Admin SDK API",
                  "containerInfo": "123456789012",
                  "consumer": "projects/123456789012"
                }
              }
            ]
          }
        }
        """;

    @Test
    void interpretSourceApiError_apiNotEnabled() {
        Optional<GoogleApiSetupErrorInterpreter.InterpretedSetupError> result =
            GoogleApiSetupErrorInterpreter.interpretSourceApiError(403, API_NOT_ENABLED_BODY);

        assertTrue(result.isPresent());
        assertEquals(ErrorCauses.SOURCE_API_NOT_ENABLED, result.get().getErrorCause());
        assertEquals(
            "{\"message\":\"GCP API is not enabled for the OAuth client project\",\"api\":\"admin.googleapis.com\",\"apiTitle\":\"Admin SDK API\"}",
            result.get().getClientResponseBody());
        assertFalse(result.get().getClientResponseBody().contains("123456789012"));
    }

    @Test
    void interpretSourceApiError_other403_returnsEmpty() {
        assertTrue(GoogleApiSetupErrorInterpreter.interpretSourceApiError(403,
            "{\"error\":{\"code\":403,\"message\":\"Insufficient Permission\"}}").isEmpty());
    }

    @Test
    void interpretSourceApiError_non403_returnsEmpty() {
        assertTrue(GoogleApiSetupErrorInterpreter.interpretSourceApiError(401, API_NOT_ENABLED_BODY)
            .isEmpty());
    }

    @Test
    void interpretTokenExchangeFailure_dwdNotGranted() {
        String message = """
            Error getting access token for service account: 401 Unauthorized
            POST https://oauth2.googleapis.com/token, iss: psoxy-example-google-meet@example-project.iam.gserviceaccount.com
            """;

        Optional<GoogleApiSetupErrorInterpreter.InterpretedSetupError> result =
            GoogleApiSetupErrorInterpreter.interpretTokenExchangeFailure(message);

        assertTrue(result.isPresent());
        assertEquals(ErrorCauses.SOURCE_DWD_NOT_GRANTED, result.get().getErrorCause());
        assertTrue(result.get().getClientResponseBody().contains("Domain-wide Delegation"));
    }

    @Test
    void interpretTokenExchangeFailure_scopeMismatch() {
        String message = """
            Error getting access token for service account: 400 Bad Request POST https://oauth2.googleapis.com/token
            { "error": "access_denied", "error_description": "..." }
            """;

        Optional<GoogleApiSetupErrorInterpreter.InterpretedSetupError> result =
            GoogleApiSetupErrorInterpreter.interpretTokenExchangeFailure(message);

        assertTrue(result.isPresent());
        assertEquals(ErrorCauses.SOURCE_OAUTH_SCOPE_MISMATCH, result.get().getErrorCause());
    }

    @Test
    void interpretTokenExchangeFailure_invalidKey() {
        String message = """
            Error getting access token for service account: 400 Bad Request POST https://oauth2.googleapis.com/token
            { "error": "invalid_grant", "error_description": "Invalid JWT Signature." }
            """;

        Optional<GoogleApiSetupErrorInterpreter.InterpretedSetupError> result =
            GoogleApiSetupErrorInterpreter.interpretTokenExchangeFailure(message);

        assertTrue(result.isPresent());
        assertEquals(ErrorCauses.SOURCE_CREDENTIALS_INVALID, result.get().getErrorCause());
    }

    @Test
    void interpretTokenExchangeFailure_unrelated_returnsEmpty() {
        assertTrue(GoogleApiSetupErrorInterpreter.interpretTokenExchangeFailure(
            "connection reset").isEmpty());
    }
}
