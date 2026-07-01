package co.worklytics.psoxy;

import java.util.Arrays;
import java.util.Optional;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * metadata fields that Psoxy may add to processed data responses.
 *
 * <p>On sync HTTP responses, only these fields are exposed as headers (via {@link #getHttpHeader()}).
 * Request-capture metadata ({@link co.worklytics.psoxy.gateway.output.ApiDataOutputUtils.OutputObjectMetadata})
 * is stored on {@link co.worklytics.psoxy.gateway.ProcessedContent} for async/side outputs only.
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

    public static Optional<ProcessedDataMetadataFields> fromMetadataKey(String metadataKey) {
        return Arrays.stream(values())
            .filter(f -> f.getMetadataKey().equals(metadataKey))
            .findFirst();
    }

}
