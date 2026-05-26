package co.worklytics.psoxy;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * metadata fields that Psoxy may add to processed data responses
 *  -- as HTTP headers on http responses
 *  -- as metadata if written to objects
 */
@RequiredArgsConstructor
public enum ProcessedDataMetadataFields {
    /**
     * SHA-1 of rules used to sanitize the response
     *
     * (exposing sha to client allows client to warn if rules change, are out-of-date, or don't
     *  support a use-case)
     *
     */
    RULES_SHA("Rules-SHA"),

    /**
     * an error code while processing the request
     */
    ERROR("Error"),

    /**
     * a warning code while processing the request
     */
    WARNING("Warning"),

    /**
     * version of the proxy that processed the request
     */
    PROXY_VERSION("Psoxy-Version"),

    /**
     * sha256 of the PII salt used to sanitize the data
     */
    PII_SALT_SHA256("PII-Salt-Sha256"),
    ;

    @NonNull
    final String formattedName;

    public String getHttpHeader() {
        return "X-Psoxy-" + formattedName;
    }

    public String getMetadataKey() {
        return formattedName;
    }

}
