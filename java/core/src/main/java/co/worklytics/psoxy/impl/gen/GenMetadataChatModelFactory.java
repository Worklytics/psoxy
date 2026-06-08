package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.resources.ResourceService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.jlama.JlamaChatModel;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Creates LangChain4j {@link ChatModel} instances for genMetadata backends.
 */
@Log
@Singleton
public class GenMetadataChatModelFactory {

    private final ResourceService resourceService;

    @Inject
    public GenMetadataChatModelFactory(@Named("ForGenMetadata") ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public boolean supports(GenMetadataConfig config) {
        return GenMetadataConfig.BACKEND_LOCAL.equalsIgnoreCase(config.getBackend());
    }

    /**
     * Do not set {@code maxTokens} on {@link JlamaChatModel}: Jlama treats that value as the
     * absolute KV-cache position ceiling (prompt + completion), not max new tokens. When unset,
     * LangChain4j defaults it to the model's {@code contextLength}. Output length is bounded by
     * {@link GenMetadataConfig#getMaxTokens()} via prompt budget reservation in
     * {@link GenMetadataPromptBudget}.
     */
    public ChatModel buildLocal(GenMetadataConfig config, Path modelCacheDir) throws Exception {
        return buildLocal(config, modelCacheDir, null);
    }

    /**
     * @param jlamaMaxTokens when non-null, passed to {@link JlamaChatModel#maxTokens(Integer)}.
     *     Jlama treats this as the absolute KV position ceiling (prompt + completion), not max new
     *     tokens — see class javadoc. Used by integration tests to reproduce the misconfiguration.
     */
    public ChatModel buildLocal(GenMetadataConfig config, Path modelCacheDir, Integer jlamaMaxTokens)
        throws Exception {
        materializeModelArchiveIfPresent(config, modelCacheDir);
        String jlamaModelName = resolveJlamaModelName(config, modelCacheDir);
        JlamaChatModel.JlamaChatModelBuilder builder = JlamaChatModel.builder()
            .modelCachePath(modelCacheDir)
            .modelName(jlamaModelName)
            .temperature(0f);
        if (jlamaMaxTokens != null) {
            builder.maxTokens(jlamaMaxTokens);
        }
        return builder.build();
    }

    String resolveJlamaModelName(GenMetadataConfig config, Path modelCacheDir) {
        Path localDir = modelCacheDir.resolve(config.localModelCacheDirName());
        if (Files.exists(localDir.resolve("config.json"))) {
            return config.localModelCacheDirName();
        }
        return config.getModelId();
    }

    void materializeModelArchiveIfPresent(GenMetadataConfig config, Path modelCacheDir) {
        String archivePath = config.localModelArchivePath();
        resourceService.getResource(archivePath).ifPresent(stream -> {
            try (InputStream in = stream) {
                Path targetDir = modelCacheDir.resolve(config.localModelCacheDirName());
                if (Files.exists(targetDir.resolve("config.json"))) {
                    return;
                }
                Files.createDirectories(targetDir);
                unzip(in, targetDir);
                log.info("Materialized genMetadata model archive to " + targetDir);
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Failed to materialize genMetadata model archive from " + archivePath, e);
                throw new RuntimeException(e);
            }
        });
    }

    private void unzip(InputStream zipStream, Path targetDir) throws Exception {
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = normalizedTarget.resolve(entry.getName()).normalize();
                if (!out.startsWith(normalizedTarget)) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out);
                }
                zis.closeEntry();
            }
        }
    }
}
