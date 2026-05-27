package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.resources.ResourceService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.jlama.JlamaChatModel;
import lombok.experimental.UtilityClass;
import lombok.extern.java.Log;

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
@UtilityClass
class GenMetadataChatModelFactory {

    static boolean supports(GenMetadataConfig config) {
        return GenMetadataConfig.BACKEND_LOCAL.equalsIgnoreCase(config.getBackend());
    }

    static ChatModel buildLocal(GenMetadataConfig config, Path modelCacheDir,
                                ResourceService resourceService) throws Exception {
        materializeModelArchiveIfPresent(config, modelCacheDir, resourceService);
        String jlamaModelName = resolveJlamaModelName(config, modelCacheDir);
        return JlamaChatModel.builder()
            .modelCachePath(modelCacheDir)
            .modelName(jlamaModelName)
            .temperature(0f)
            .maxTokens(config.getMaxTokens())
            .build();
    }

    static String resolveJlamaModelName(GenMetadataConfig config, Path modelCacheDir) {
        Path localDir = modelCacheDir.resolve(config.localModelCacheDirName());
        if (Files.exists(localDir.resolve("config.json"))) {
            return config.localModelCacheDirName();
        }
        return config.getModelId();
    }

    static void materializeModelArchiveIfPresent(GenMetadataConfig config, Path modelCacheDir,
                                                 ResourceService resourceService) {
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

    private static void unzip(InputStream zipStream, Path targetDir) throws Exception {
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
