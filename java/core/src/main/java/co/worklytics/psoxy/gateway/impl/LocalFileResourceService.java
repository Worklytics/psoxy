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
        Optional<Path> resolvedOpt = resolveSafePath(objectPath);
        if (resolvedOpt.isEmpty()) {
            return Optional.empty();
        }
        Path resolved = resolvedOpt.get();
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
        return resolveSafePath(objectPath)
                .map(resolved -> Files.exists(resolved) && Files.isRegularFile(resolved))
                .orElse(false);
    }

    private Optional<Path> resolveSafePath(String objectPath) {
        if (objectPath == null || objectPath.trim().isEmpty()) {
            log.log(Level.WARNING, "Rejected null or empty objectPath");
            return Optional.empty();
        }

        if (objectPath.indexOf('\0') != -1) {
            log.log(Level.WARNING, "Rejected objectPath containing null byte");
            return Optional.empty();
        }

        // Reject absolute paths
        if (objectPath.startsWith("/") || objectPath.startsWith("\\")) {
            log.log(Level.WARNING, "Rejected objectPath starting with separator: {0}", objectPath);
            return Optional.empty();
        }

        // Reject traversal or current-directory segments
        for (String segment : objectPath.split("[/\\\\]")) {
            if (".".equals(segment) || "..".equals(segment)) {
                log.log(Level.WARNING, "Rejected objectPath containing '.' or '..' segment: {0}", objectPath);
                return Optional.empty();
            }
        }

        try {
            Path objPath = Paths.get(objectPath);
            if (objPath.isAbsolute()) {
                log.log(Level.WARNING, "Rejected absolute objectPath: {0}", objectPath);
                return Optional.empty();
            }

            Path base = Paths.get(basePath).toAbsolutePath().normalize();
            Path resolved = base.resolve(objPath).toAbsolutePath().normalize();

            // Ensure the resolved path starts with base path
            if (!resolved.startsWith(base)) {
                log.log(Level.WARNING, "Path traversal attempt detected. Object path: {0} resolved outside base path: {1}",
                        new Object[]{objectPath, base});
                return Optional.empty();
            }

            return Optional.of(resolved);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to resolve path for objectPath: " + objectPath, e);
            return Optional.empty();
        }
    }
}
