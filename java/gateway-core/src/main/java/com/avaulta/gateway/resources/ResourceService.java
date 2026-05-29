package com.avaulta.gateway.resources;

import java.io.InputStream;
import java.util.Optional;

/**
 * Abstraction for fetching binary resources (rule files, NLP models, LLMs, etc.) by object path.
 *
 * <p>Implementations may back onto local filesystem, S3, GCS, or composites thereof.
 * Return type is {@link InputStream} so callers can stream large resources without buffering
 * the entire payload in memory. Callers are responsible for closing the returned stream.</p>
 */
public interface ResourceService {

    /**
     * Default local path for instance-scoped resources.
     *
     * <p>Checked automatically — no env var needed. Lambda/Cloud Function filesystem at this path
     * can be populated via deployment layers or init scripts.</p>
     */
    String DEFAULT_LOCAL_RESOURCE_PATH = "/var/psoxy/resources";

    /**
     * Get a resource as an InputStream.
     *
     * @param objectPath path to the resource (e.g., "rules.yaml", "opennlp/en-sent.bin")
     * @return filled Optional containing an open InputStream if the resource exists; empty otherwise.
     *         Caller MUST close the returned InputStream.
     */
    Optional<InputStream> getResource(String objectPath);

    /**
     * Validates that the object path is safe (not null/empty, no null bytes, not absolute, no path traversal).
     */
    static void validatePath(String objectPath) {
        if (objectPath == null || objectPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Object path must not be null or empty");
        }

        if (objectPath.indexOf('\0') != -1) {
            throw new IllegalArgumentException("Object path must not contain null bytes");
        }

        if (objectPath.startsWith("/") || objectPath.startsWith("\\")) {
            throw new IllegalArgumentException("Object path must not start with a separator: " + objectPath);
        }

        for (String segment : objectPath.split("[/\\\\]")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Object path must not contain '.' or '..' segments: " + objectPath);
            }
        }
    }

    /**
     * Normalizes a bucket object key prefix from secret/config naming ({@code psoxy-dev-erik_}) to
     * object-key hierarchy ({@code psoxy-dev-erik/}). Strips a leading {@code /}; converts a
     * trailing {@code _} to {@code /}.
     */
    static String normalizeObjectKeyPrefix(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isEmpty()) {
            return "";
        }
        String normalized = pathPrefix.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1) + "/";
        }
        return normalized;
    }
}
