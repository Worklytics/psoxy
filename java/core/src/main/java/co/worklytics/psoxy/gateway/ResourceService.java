package co.worklytics.psoxy.gateway;

import java.io.InputStream;
import java.util.Optional;

/**
 * Abstraction for fetching binary resources (rule files, NLP models, LLMs, etc.) by object path.
 *
 * <p>Implementations may back onto local filesystem, S3, GCS, or composites thereof.
 * Return type is {@link InputStream} so callers can stream large resources without buffering
 * the entire payload in memory. Callers are responsible for closing the returned stream.</p>
 *
 * <p>Analogous to {@link ConfigService} but for binary/large payloads rather than string config values.</p>
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
     * @param objectPath path to the resource (e.g., "rules.yaml", "models/en-sent.bin")
     * @return filled Optional containing an open InputStream if the resource exists; empty otherwise.
     *         Caller MUST close the returned InputStream.
     */
    Optional<InputStream> getResource(String objectPath);

    /**
     * Check whether a resource exists at the given path without opening a stream.
     *
     * <p>Default implementation attempts to open and immediately close the resource.
     * Implementations should override for efficiency where possible.</p>
     *
     * @param objectPath path to the resource
     * @return true if the resource exists
     */
    default boolean exists(String objectPath) {
        Optional<InputStream> resource = getResource(objectPath);
        resource.ifPresent(is -> {
            try {
                is.close();
            } catch (Exception e) {
                // swallow
            }
        });
        return resource.isPresent();
    }
}
