package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.augments.GenMetadataBackend;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.java.Log;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * genMetadata inference via LangChain4j {@link ChatModel} (Bedrock or Vertex only).
 *
 * <p>Thread-safety: lazy per-modelId client init via {@link ConcurrentHashMap#computeIfAbsent};
 * concurrent cloud calls limited by semaphore.
 */
@Log
public class LangChain4jGenMetadataBackend implements GenMetadataBackend {

    static final int CLOUD_MAX_CONCURRENT = 4;

    static final class ModelHandle {
        final ChatModel chatModel;
        final Exception failure;

        private ModelHandle(ChatModel chatModel, Exception failure) {
            this.chatModel = chatModel;
            this.failure = failure;
        }

        static ModelHandle ready(ChatModel chatModel) {
            return new ModelHandle(chatModel, null);
        }

        static ModelHandle failed(Exception e) {
            return new ModelHandle(null, e);
        }

        boolean isReady() {
            return chatModel != null;
        }
    }

    private final GenMetadataConfig config;
    private final ObjectMapper objectMapper;
    private final GenMetadataPromptBudget promptBudget;
    private final GenMetadataChatModelFactory chatModelFactory;

    private final ConcurrentHashMap<String, ModelHandle> models = new ConcurrentHashMap<>();
    private final Semaphore cloudConcurrency = new Semaphore(CLOUD_MAX_CONCURRENT);
    private final ExecutorService chatExecutor = Executors.newCachedThreadPool(chatThreadFactory());

    public LangChain4jGenMetadataBackend(GenMetadataConfig config, ObjectMapper objectMapper,
                                         GenMetadataPromptBudget promptBudget,
                                         GenMetadataChatModelFactory chatModelFactory) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.promptBudget = promptBudget;
        this.chatModelFactory = chatModelFactory;
    }

    @Override
    public Object generate(String taskPrompt, JsonSchemaFilter outputSchema, String inputData) {
        ModelHandle handle = resolveModel();
        if (!handle.isReady()) {
            return null;
        }

        String fittedInput = promptBudget.fitInputData(
            inputData,
            taskPrompt,
            outputSchema,
            config.getMaxInputChars(),
            assumedContextLength(),
            config.getMaxTokens());
        List<ChatMessage> messages =
            GenMetadataPromptBuilder.toMessages(taskPrompt, outputSchema, fittedInput, objectMapper);
        Optional<ResponseFormat> responseFormat =
            GenMetadataResponseFormats.fromOutputSchema(outputSchema);

        boolean permitAcquired = false;
        try {
            permitAcquired = cloudConcurrency.tryAcquire(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!permitAcquired) {
                log.warning("genMetadata cloud concurrency timeout for model " + config.getModelId()
                    + " after " + config.getTimeoutSeconds() + "s");
                return null;
            }

            Instant inferenceStartedAt = Instant.now();
            long inferenceStartedNanos = System.nanoTime();
            log.info("genMetadata LLM inference started at " + inferenceStartedAt
                + " modelId=" + config.getModelId()
                + " backend=" + config.getBackend());
            ChatResponse response;
            try {
                response = chatWithTimeout(handle.chatModel, messages, responseFormat.orElse(null));
            } finally {
                long inferenceMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - inferenceStartedNanos);
                log.info("genMetadata LLM inference completed in " + inferenceMs + "ms"
                    + " modelId=" + config.getModelId());
            }
            if (response == null || response.aiMessage() == null) {
                return null;
            }
            String text = response.aiMessage().text();
            if (text != null && !text.isBlank()) {
                log.info("genMetadata raw model response: " + truncateForLog(text));
            }
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            if (isAuthOrQuotaFailure(e)) {
                log.log(Level.WARNING,
                    "genMetadata cloud inference denied/rate-limited (backend="
                        + config.getBackend() + "); omitting augment", e);
            } else {
                log.log(Level.WARNING, "genMetadata inference failed", e);
            }
            return null;
        } finally {
            if (permitAcquired) {
                cloudConcurrency.release();
            }
        }
    }

    int assumedContextLength() {
        return 8192;
    }

    ModelHandle resolveModel() {
        return models.computeIfAbsent(config.getModelId(), this::createModelHandle);
    }

    private ModelHandle createModelHandle(String modelKey) {
        try {
            ChatModel chatModel = chatModelFactory.create(config, null);
            log.info("Initialized genMetadata LangChain4j client: " + modelKey
                + " backend=" + config.getBackend());
            return ModelHandle.ready(chatModel);
        } catch (Exception e) {
            log.log(Level.WARNING,
                "Failed to initialize genMetadata client '" + modelKey + "' backend="
                    + config.getBackend(),
                e);
            return ModelHandle.failed(e);
        }
    }

    ChatResponse chatWithTimeout(ChatModel chatModel, List<ChatMessage> messages,
                                 ResponseFormat responseFormat) throws Exception {
        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(messages);
        if (responseFormat != null) {
            requestBuilder.responseFormat(responseFormat);
        }
        ChatRequest request = requestBuilder.build();
        Future<ChatResponse> future = chatExecutor.submit(() -> chatModel.chat(request));
        try {
            return future.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warning("genMetadata LLM inference timed out after " + config.getTimeoutSeconds()
                + "s modelId=" + config.getModelId());
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    static boolean isAuthOrQuotaFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String name = c.getClass().getName();
            String msg = c.getMessage() != null ? c.getMessage().toLowerCase() : "";
            if (name.contains("AccessDenied")
                || name.contains("Authorization")
                || name.contains("PermissionDenied")
                || name.contains("ResourceExhausted")
                || name.contains("Throttling")
                || msg.contains("access denied")
                || msg.contains("not authorized")
                || msg.contains("quota")
                || msg.contains("throttl")
                || msg.contains("rate exceeded")
                || msg.contains("429")
                || msg.contains("403")) {
                return true;
            }
        }
        return false;
    }

    private static ThreadFactory chatThreadFactory() {
        AtomicInteger threadNumber = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "genMetadata-chat-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final int MAX_LOG_OUTPUT_CHARS = 2000;

    private static String truncateForLog(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= MAX_LOG_OUTPUT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_LOG_OUTPUT_CHARS) + "... (" + value.length() + " chars total)";
    }
}
