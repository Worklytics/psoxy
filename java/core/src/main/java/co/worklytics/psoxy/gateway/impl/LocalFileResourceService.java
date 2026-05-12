package co.worklytics.psoxy.gateway.impl;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;

import co.worklytics.psoxy.gateway.ResourceService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * ResourceService backed by the local filesystem.
 *
 * <p>Resolves resources relative to a configured base path. Useful for local development
 * and as the preferred layer before remote cloud storage lookups.</p>
 */
@Log
@RequiredArgsConstructor
public class LocalFileResourceService implements ResourceService {

    /**
     * Base directory path under which to look for resources.
     */
    @NonNull
    final String basePath;

    @Override
    public Optional<InputStream> getResource(String objectPath) {
        Path resolved = Paths.get(basePath, objectPath);
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            log.log(Level.FINE, "Local resource not found: {0}", resolved);
            return Optional.empty();
        }

        try {
            log.log(Level.INFO, "Loading resource from local filesystem: {0}", resolved);
            return Optional.of(new FileInputStream(resolved.toFile()));
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to open local resource: " + resolved, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String objectPath) {
        Path resolved = Paths.get(basePath, objectPath);
        return Files.exists(resolved) && Files.isRegularFile(resolved);
    }
}
