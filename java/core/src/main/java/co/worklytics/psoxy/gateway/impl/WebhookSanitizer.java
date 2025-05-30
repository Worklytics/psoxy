package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.HttpEventRequest;

import java.util.Map;

public interface WebhookSanitizer {

    /**
     *  whether this sanitizer can handle the request, based on its rules (policy)
     */
    boolean canAccept(HttpEventRequest request);


    /**
     * whether the request is consistent with the claims provided (usually, from an Authorization header).
     *
     * eg, verifies integrity of the request; that it is not forged if the claims were properly signed.
     *
     * @param request to check against the claims.
     * @param claims to check against the request, according to the WebhookRules (policy) of this sanitizer.
     * @return true if the claims are consistent with the request, false otherwise.
     */
    boolean verifyClaims(HttpEventRequest request, Map<String, String> claims);

    /**
     *  sanitize the request, removing sensitive information according to the rules (policy) of this sanitizer.
     *
     * @return sanitized request body as a String
     * @throws IllegalArgumentException if the request is not accepted by this sanitizer
     */
    String sanitize(HttpEventRequest request);
}
