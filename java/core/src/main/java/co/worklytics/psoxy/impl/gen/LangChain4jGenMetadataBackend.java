package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.resources.ResourceService;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.augments.GenMetadataBackend;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.java.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * genMetadata inference via LangChain4j {@link ChatModel} (Jlama for local embedded models).
 *
 * <p>Thread-safety: per-{@link GenMetadataConfig#getModelId()} {@link #loadLocks} give single-flight
 * model load and zip extraction; {@link #inferenceLocks} serialize {@link ChatModel} use (Jlama is not
 * safe for concurrent inference on one instance). {@link ObjectMapper} is shared read-only.
 */
@Log
public class LangChain4jGenMetadataBackend implements GenMetadataBackend {

    enum LoadState {
        ABSENT, LOADING, READY, FAILED
    }

    static final class ModelHandle {
        final LoadState state;
        final ChatModel chatModel;
        final Exception failure;

        ModelHandle(LoadState state, ChatModel chatModel, Exception failure) {
            this.state = state;
            this.chatModel = chatModel;
            this.failure = failure;
        }

        static ModelHandle loading() {
            return new ModelHandle(LoadState.LOADING, null, null);
        }

        static ModelHandle ready(ChatModel chatModel) {
            return new ModelHandle(LoadState.READY, chatModel, null);
        }

        static ModelHandle failed(Exception e) {
            return new ModelHandle(LoadState.FAILED, null, e);
        }
    }

    private final GenMetadataConfig config;
    private final ResourceService resourceService;
    private final ObjectMapper objectMapper;
    /**
     * Writable Jlama cache (extracted zips, HF downloads). Not the same as {@link ResourceService},
     * which only supplies read-only model archives from remote storage.
     */
    @Getter(AccessLevel.PACKAGE)
    private final Path modelCacheDir;

    private final ConcurrentHashMap<String, ModelHandle> models = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> loadLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> inferenceLocks = new ConcurrentHashMap<>();

    public LangChain4jGenMetadataBackend(GenMetadataConfig config, ResourceService resourceService,
                                         ObjectMapper objectMapper) {
        this.config = config;
        this.resourceService = resourceService;
        this.objectMapper = objectMapper;
        try {
            this.modelCacheDir = Files.createTempDirectory("psoxy-jlama-cache");
            this.modelCacheDir.toFile().deleteOnExit();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create genMetadata model cache directory", e);
        }
    }

    @Override
    public Object generate(String taskPrompt, JsonSchemaFilter outputSchema, String inputData) {
        ModelHandle handle = resolveModel();
        if (handle.state != LoadState.READY || handle.chatModel == null) {
            return null;
        }

        List<ChatMessage> messages =
            GenMetadataPromptBuilder.toMessages(taskPrompt, outputSchema, inputData, objectMapper);

        ReentrantLock inferenceLock =
            inferenceLocks.computeIfAbsent(config.getModelId(), k -> new ReentrantLock());
        boolean acquired = false;
        try {
            acquired = inferenceLock.tryLock(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                log.warning("genMetadata inference lock timeout for model " + config.getModelId());
                return null;
            }
            ChatResponse response = handle.chatModel.chat(ChatRequest.builder()
                .messages(messages)
                .build());
            if (response == null || response.aiMessage() == null) {
                return null;
            }
            return response.aiMessage().text();
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
                ChatModel chatModel =
                    GenMetadataChatModelFactory.buildLocal(config, modelCacheDir, resourceService);
                ModelHandle ready = ModelHandle.ready(chatModel);
                models.put(modelKey, ready);
                log.info("Loaded genMetadata LangChain4j model: " + modelKey);
                return ready;
            } catch (Exception e) {
                log.log(Level.WARNING,
                    "Failed to load genMetadata model '" + modelKey + "' (archive path: "
                        + config.localModelArchivePath() + ")", e);
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

}
