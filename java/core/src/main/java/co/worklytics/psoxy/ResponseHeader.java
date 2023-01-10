package co.worklytics.psoxy;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * headers that Psoxy may return in responses
 */
@RequiredArgsConstructor
public enum ResponseHeader {
    /**
     * SHA-1 of rules used to sanitize the response
     *
     * (exposing sha to client allows client to warn if rules change, are out-of-date, or don't
     *  support a use-case)
     *
     */
    RULES_SHA("Rules-SHA"),
    ERROR("Error"),
    PROXY_SOURCE_AUTH("Source-Auth");

    @NonNull
    final String httpNamePart;

    public String getHttpHeader() {
        return "X-Psoxy-" + httpNamePart;
    }
}
