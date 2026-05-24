package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.resources.ResourceService;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.augments.GenMetadataBackend;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import lombok.extern.java.Log;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * Local GGUF inference via java-llama.cpp with single-flight model loading and per-model inference locks.
 */
@Log
public class LlamaCppLocalBackend implements GenMetadataBackend {

    private static final String LLM_RESOURCE_PREFIX = "llm/";

    private static final String PROMPT_TEMPLATE = """
        You are a data-processing component in a privacy proxy.
        Task: %s

        Respond with exactly one JSON value (no markdown, no prose).
        The JSON MUST validate against this JSON Schema:
        %s

        Input data to process:
        %s
        """;

    enum LoadState {
        ABSENT, LOADING, READY, FAILED
    }

    static final class ModelHandle {
        final LoadState state;
        final LlamaModel model;
        final Path modelPath;
        final Exception failure;

        ModelHandle(LoadState state, LlamaModel model, Path modelPath, Exception failure) {
            this.state = state;
            this.model = model;
            this.modelPath = modelPath;
            this.failure = failure;
        }

        static ModelHandle loading() {
            return new ModelHandle(LoadState.LOADING, null, null, null);
        }

        static ModelHandle ready(LlamaModel model, Path path) {
            return new ModelHandle(LoadState.READY, model, path, null);
        }

        static ModelHandle failed(Exception e) {
            return new ModelHandle(LoadState.FAILED, null, null, e);
        }
    }

    private final GenMetadataConfig config;
    private final ResourceService resourceService;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, ModelHandle> models = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> loadLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> inferenceLocks = new ConcurrentHashMap<>();

    public LlamaCppLocalBackend(GenMetadataConfig config, ResourceService resourceService,
                                ObjectMapper objectMapper) {
        this.config = config;
        this.resourceService = resourceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Object generate(String taskPrompt, JsonSchemaFilter outputSchema, String inputData) {
        ModelHandle handle = resolveModel();
        if (handle.state != LoadState.READY || handle.model == null) {
            return null;
        }

        String prompt = buildPrompt(taskPrompt, outputSchema, inputData);
        int tokenCap = config.getMaxTokens();

        ReentrantLock inferenceLock =
            inferenceLocks.computeIfAbsent(config.getModelId(), k -> new ReentrantLock());
        boolean acquired = false;
        try {
            acquired = inferenceLock.tryLock(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                log.warning("genMetadata inference lock timeout for model " + config.getModelId());
                return null;
            }
            InferenceParameters params = new InferenceParameters(prompt)
                .setTemperature(0f)
                .setTopP(1f)
                .setNPredict(tokenCap);
            return handle.model.complete(params);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.log(Level.WARNING, "genMetadata local inference failed", e);
            return null;
        } finally {
            if (acquired) {
                inferenceLock.unlock();
            }
        }
    }

    String buildPrompt(String taskPrompt, JsonSchemaFilter outputSchema, String inputData) {
        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(outputSchema);
        } catch (Exception e) {
            schemaJson = "{}";
        }
        return PROMPT_TEMPLATE.formatted(taskPrompt.trim(), schemaJson, inputData);
    }

    ModelHandle resolveModel() {
        String modelKey = config.getModelId();
        ModelHandle existing = models.get(modelKey);
        if (existing != null) {
            return switch (existing.state) {
                case READY, FAILED -> existing;
                case LOADING -> waitForLoad(modelKey);
                case ABSENT -> loadModel(modelKey);
            };
        }
        return loadModel(modelKey);
    }

    private ModelHandle loadModel(String modelKey) {
        ReentrantLock loadLock = loadLocks.computeIfAbsent(modelKey, k -> new ReentrantLock());
        loadLock.lock();
        try {
            ModelHandle existing = models.get(modelKey);
            if (existing != null) {
                if (existing.state == LoadState.LOADING) {
                    return waitForLoad(modelKey);
                }
                return existing;
            }
            models.put(modelKey, ModelHandle.loading());
            try {
                Path modelPath = materializeModel(config.localModelObjectPath());
                ModelParameters modelParams = new ModelParameters().setModel(modelPath.toString());
                LlamaModel model = new LlamaModel(modelParams);
                ModelHandle ready = ModelHandle.ready(model, modelPath);
                models.put(modelKey, ready);
                log.info("Loaded genMetadata local model: " + modelKey);
                return ready;
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Failed to load genMetadata model '" + modelKey + "' from "
                        + config.localModelObjectPath(), e);
                ModelHandle failed = ModelHandle.failed(e);
                models.put(modelKey, failed);
                return failed;
            }
        } finally {
            loadLock.unlock();
        }
    }

    private ModelHandle waitForLoad(String modelKey) {
        long deadline = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(config.getTimeoutSeconds());
        while (System.nanoTime() < deadline) {
            ModelHandle handle = models.get(modelKey);
            if (handle != null && handle.state != LoadState.LOADING) {
                return handle;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ModelHandle.failed(e);
            }
        }
        log.warning("Timed out waiting for genMetadata model load: " + modelKey);
        return models.getOrDefault(modelKey, ModelHandle.failed(
            new IllegalStateException("model load timeout")));
    }

    Path materializeModel(String objectPath) throws Exception {
        return resourceService.getResource(objectPath)
            .map(stream -> {
                try {
                    Path temp = Files.createTempFile("psoxy-gen-", ".gguf");
                    temp.toFile().deleteOnExit();
                    try (InputStream in = stream) {
                        Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return temp;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .orElseThrow(() -> new IllegalStateException(
                "genMetadata model not found at resource path: " + objectPath
                    + " (also checked " + ResourceService.DEFAULT_LOCAL_RESOURCE_PATH + "/"
                    + objectPath + ")"));
    }

    /** Release native resources. For tests. */
    void close() {
        for (Map.Entry<String, ModelHandle> entry : models.entrySet()) {
            if (entry.getValue().model != null) {
                try {
                    entry.getValue().model.close();
                } catch (Exception e) {
                    log.log(Level.FINE, "Error closing model " + entry.getKey(), e);
                }
            }
        }
        models.clear();
    }
}
