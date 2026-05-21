package com.avaulta.gateway.resources;

import java.io.InputStream;
import java.util.Optional;

/**
 * Provides binary resources (e.g. NLP model files) by relative path.
 *
 * <p>Implementations may resolve from local filesystem, remote object storage, etc.
 * Callers are responsible for closing returned streams.</p>
 */
public interface BinaryResourceProvider {

    /**
     * Open a resource by relative path (e.g. {@code opennlp/en-sent.bin}).
     *
     * @return filled Optional containing an open InputStream if found; empty otherwise
     */
    Optional<InputStream> open(String relativePath);
}
