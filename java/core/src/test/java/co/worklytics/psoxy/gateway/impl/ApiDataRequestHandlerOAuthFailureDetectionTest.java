package co.worklytics.psoxy.gateway.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiDataRequestHandlerOAuthFailureDetectionTest {

    @Test
    void detectsGoogleAndMicrosoftTokenEndpointFailures() {
        assertTrue(ApiDataRequestHandler.isOAuthTokenExchangeFailure(
            new IOException("Error getting access token for service account: 401 Unauthorized POST https://oauth2.googleapis.com/token")));
        assertTrue(ApiDataRequestHandler.isOAuthTokenExchangeFailure(
            new IOException("OAuth token request failed: 401 Unauthorized POST https://login.microsoftonline.com/tenant/oauth2/v2.0/token body={...}")));
        assertTrue(ApiDataRequestHandler.isOAuthTokenExchangeFailure(
            new RuntimeException(new IOException("oauth token request failed: 400"))));
        assertFalse(ApiDataRequestHandler.isOAuthTokenExchangeFailure(
            new IOException("Connection refused to graph.microsoft.com")));
    }
}
