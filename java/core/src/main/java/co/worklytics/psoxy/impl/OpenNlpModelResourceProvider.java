package co.worklytics.psoxy.impl;

import java.io.InputStream;
import java.util.Optional;

import com.avaulta.gateway.resources.BinaryResourceProvider;

import co.worklytics.psoxy.gateway.ResourceService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Resolves OpenNLP model files via {@link ResourceService}, checking instance-scoped resources
 * (local filesystem + instance remote path) before shared remote resources.
 */
@RequiredArgsConstructor
public class OpenNlpModelResourceProvider implements BinaryResourceProvider {

    static final String PATH_PREFIX = "opennlp/";

    @NonNull
    final ResourceService instanceResourceService;

    @NonNull
    final ResourceService sharedRemoteResourceService;

    @Override
    public Optional<InputStream> open(String relativePath) {
        ResourceService.validatePath(relativePath);
        if (!relativePath.startsWith(PATH_PREFIX)) {
            throw new IllegalArgumentException("OpenNLP model path must start with '" + PATH_PREFIX + "': " + relativePath);
        }
        return instanceResourceService.getResource(relativePath)
            .or(() -> sharedRemoteResourceService.getResource(relativePath));
    }

    /**
     * Convenience for model filenames (e.g. {@code en-sent.bin}).
     */
    public Optional<InputStream> openModel(String modelFileName) {
        return open(PATH_PREFIX + modelFileName);
    }
}
