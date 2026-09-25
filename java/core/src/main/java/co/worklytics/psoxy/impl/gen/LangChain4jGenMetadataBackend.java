package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
import com.avaulta.gateway.rules.augments.GenMetadataBackend;
import com.avaulta.gateway.rules.JsonSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * genMetadata inference via LangChain4j {@link ChatModel} (Bedrock or Vertex only).
 *
 * <p>Thread-safety: lazy per-modelId client init via {@link ConcurrentHashMap#computeIfAbsent};
 * concurrent cloud calls limited to {@link #CLOUD_MAX_CONCURRENT}. Wait for a slot plus the LLM
 * call share {@link GenMetadataConfig#getTimeoutSeconds()} (default 15).
 */
@Log
@Singleton
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
    private final GenMetadataTokenUsageAccumulator tokenUsageAccumulator;
    private final GenMetadataPromptBuilder promptBuilder;
    private final GenMetadataResponseFormats responseFormats;

    private final ConcurrentHashMap<String, ModelHandle> models = new ConcurrentHashMap<>();
    private final Semaphore cloudSlots = new Semaphore(CLOUD_MAX_CONCURRENT, true);
    private final ExecutorService chatExecutor = new ThreadPoolExecutor(
        CLOUD_MAX_CONCURRENT,
        CLOUD_MAX_CONCURRENT,
        0L,
        TimeUnit.MILLISECONDS,
        new SynchronousQueue<>(),
        chatThreadFactory(),
        new ThreadPoolExecutor.AbortPolicy());

    @Inject
    public LangChain4jGenMetadataBackend(GenMetadataConfig config,
                                         ObjectMapper objectMapper,
                                         GenMetadataPromptBudget promptBudget,
                                         GenMetadataChatModelFactory chatModelFactory,
                                         GenMetadataTokenUsageAccumulator tokenUsageAccumulator,
                                         GenMetadataPromptBuilder promptBuilder,
                                         GenMetadataResponseFormats responseFormats) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.promptBudget = promptBudget;
        this.chatModelFactory = chatModelFactory;
        this.tokenUsageAccumulator = tokenUsageAccumulator;
        this.promptBuilder = promptBuilder;
        this.responseFormats = responseFormats;
    }

    @Override
    public Object generate(String taskPrompt, JsonSchema outputSchema, String inputData) {
        return generate(taskPrompt, outputSchema, inputData, null, null);
    }

    @Override
    public Object generate(String taskPrompt, JsonSchema outputSchema, String inputData,
                           Integer maxOutputTokens) {
        return generate(taskPrompt, outputSchema, inputData, maxOutputTokens, null);
    }

    @Override
    public Object generate(String taskPrompt, JsonSchema outputSchema, String inputData,
                           Integer maxOutputTokens, Integer maxInputTokens) {
        ModelHandle handle = requireReadyHandle();

        int effectiveMaxTokens = effectiveMaxTokens(maxOutputTokens);
        int effectiveMaxInputTokens = effectiveMaxInputTokens(maxInputTokens);
        String fittedInput = promptBudget.fitDynamicInput(inputData, effectiveMaxInputTokens);
        List<ChatMessage> messages =
            promptBuilder.toMessages(taskPrompt, outputSchema, fittedInput);
        // Bedrock Converse maps ResponseFormat → outputConfig; Amazon Nova (our default) rejects it.
        boolean bedrock = config.getBackend() == GenMetadataConfig.Backend.BEDROCK;
        Optional<ResponseFormat> responseFormat = bedrock
            ? Optional.empty()
            : responseFormats.fromOutputSchema(outputSchema);
        if (bedrock) {
            log.info("genMetadata Bedrock: omitting ResponseFormat/outputConfig for model "
                + config.getModelId());
        }
        return completeChat(handle, messages, responseFormat.orElse(null), effectiveMaxTokens);
    }

    @Override
    public Object classify(String taskPrompt, List<String> classes, String inputData,
                           int maxOutputTokens, Integer maxInputTokens) {
        ModelHandle handle = requireReadyHandle();

        int effectiveMaxInputTokens = effectiveMaxInputTokens(maxInputTokens);
        String fittedInput = promptBudget.fitDynamicInput(inputData, effectiveMaxInputTokens);
        List<ChatMessage> messages =
            promptBuilder.toClassifyMessages(taskPrompt, classes, fittedInput);
        boolean bedrock = config.getBackend() == GenMetadataConfig.Backend.BEDROCK;
        Optional<ResponseFormat> responseFormat = bedrock
            ? Optional.empty()
            : responseFormats.fromClasses(classes);
        if (bedrock) {
            log.info("classify Bedrock: omitting ResponseFormat/outputConfig for model "
                + config.getModelId());
        }
        return completeChat(handle, messages, responseFormat.orElse(null), maxOutputTokens);
    }

    private ModelHandle requireReadyHandle() {
        if (!chatModelFactory.supports(config)) {
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                "genMetadata backend is not available");
        }
        ModelHandle handle = resolveModel();
        if (!handle.isReady()) {
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                "genMetadata client failed to initialize", handle.failure);
        }
        return handle;
    }

    private Object completeChat(ModelHandle handle, List<ChatMessage> messages,
                                ResponseFormat responseFormat, int maxOutputTokens) {
        try {
            Instant inferenceStartedAt = Instant.now();
            long inferenceStartedNanos = System.nanoTime();
            log.info("genMetadata LLM inference started at " + inferenceStartedAt
                + " modelId=" + config.getModelId()
                + " backend=" + backendLabel()
                + thinkingLogSuffix());
            ChatResponse response;
            try {
                response = chatWithTimeout(handle.chatModel, messages, responseFormat,
                    maxOutputTokens);
            } finally {
                long inferenceMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - inferenceStartedNanos);
                log.info("genMetadata LLM inference completed in " + inferenceMs + "ms"
                    + " modelId=" + config.getModelId());
            }
            if (response == null || response.aiMessage() == null) {
                return null;
            }
            recordTokenUsage(response);
            String text = response.aiMessage().text();
            if (text != null && !text.isBlank()) {
                log.info("genMetadata model response received"
                    + " chars=" + text.length()
                    + " modelId=" + config.getModelId());
            }
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (GenMetadataAugmentException e) {
            throw e;
        } catch (Exception e) {
            if (isAuthFailure(e) || isQuotaFailure(e)) {
                log.log(Level.WARNING,
                    "genMetadata cloud inference denied/rate-limited (backend="
                        + backendLabel() + "); omitting augment", e);
                throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                    "genMetadata cloud inference denied or rate-limited", e);
            }
            log.log(Level.WARNING, "genMetadata inference failed", e);
            return null;
        }
    }

    int effectiveMaxInputTokens(Integer ruleMaxInputTokens) {
        if (ruleMaxInputTokens != null && ruleMaxInputTokens > 0) {
            return ruleMaxInputTokens;
        }
        return Augment.GenMetadata.DEFAULT_MAX_INPUT_TOKENS;
    }

    ModelHandle resolveModel() {
        return models.computeIfAbsent(config.getModelId(), this::createModelHandle);
    }

    private ModelHandle createModelHandle(String modelKey) {
        try {
            ChatModel chatModel = chatModelFactory.create(config, null);
            log.info("Initialized genMetadata LangChain4j client: " + modelKey
                + " backend=" + backendLabel()
                + thinkingLogSuffix());
            return ModelHandle.ready(chatModel);
        } catch (Exception e) {
            log.log(Level.WARNING,
                "Failed to initialize genMetadata client '" + modelKey + "' backend="
                    + backendLabel(),
                e);
            return ModelHandle.failed(e);
        }
    }

    private String backendLabel() {
        if (config instanceof GenMetadataConfig.Unsupported unsupported) {
            return unsupported.getRawBackend();
        }
        return config.getBackend() != null ? config.getBackend().configValue() : "unknown";
    }

    private String thinkingLogSuffix() {
        String extra = config.extraLogSuffix();
        return extra != null ? extra : "";
    }

    int effectiveMaxTokens(Integer ruleMaxTokens) {
        if (ruleMaxTokens != null && ruleMaxTokens > 0) {
            return ruleMaxTokens;
        }
        return Augment.GenMetadata.DEFAULT_MAX_OUTPUT_TOKENS;
    }

    ChatResponse chatWithTimeout(ChatModel chatModel, List<ChatMessage> messages,
                                 ResponseFormat responseFormat, int maxOutputTokens) throws Exception {
        ChatRequest.Builder requestBuilder = ChatRequest.builder()
            .messages(messages)
            .maxOutputTokens(maxOutputTokens);
        if (responseFormat != null) {
            requestBuilder.responseFormat(responseFormat);
        }
        ChatRequest request = requestBuilder.build();
        int timeoutSeconds = config.getTimeoutSeconds();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        boolean acquired = false;
        try {
            long waitNanos = deadlineNanos - System.nanoTime();
            if (waitNanos <= 0
                || !cloudSlots.tryAcquire(waitNanos, TimeUnit.NANOSECONDS)) {
                log.warning("genMetadata timed out after " + timeoutSeconds
                    + "s waiting for an inference slot modelId=" + config.getModelId());
                return null;
            }
            acquired = true;
            Future<ChatResponse> future;
            try {
                future = chatExecutor.submit(() -> chatModel.chat(request));
            } catch (RejectedExecutionException e) {
                log.warning("genMetadata inference rejected; all workers busy modelId="
                    + config.getModelId());
                return null;
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                future.cancel(true);
                drainCancelled(future);
                log.warning("genMetadata LLM inference timed out after " + timeoutSeconds
                    + "s modelId=" + config.getModelId());
                return null;
            }
            try {
                return future.get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                log.warning("genMetadata LLM inference timed out after " + timeoutSeconds
                    + "s modelId=" + config.getModelId());
                drainCancelled(future);
                return null;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw e;
            }
        } finally {
            if (acquired) {
                cloudSlots.release();
            }
        }
    }

    private void drainCancelled(Future<ChatResponse> future) {
        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (CancellationException ignored) {
            // cancel(true) won; worker stopped
        } catch (TimeoutException ignored) {
            // SDK call still running; the fixed pool bounds additional in-flight work
        } catch (ExecutionException ignored) {
            // worker finished with an error after we timed out
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordTokenUsage(ChatResponse response) {
        var usage = response.tokenUsage();
        if (usage == null && response.metadata() != null) {
            usage = response.metadata().tokenUsage();
        }
        Integer input = usage != null ? usage.inputTokenCount() : null;
        Integer output = usage != null ? usage.outputTokenCount() : null;
        tokenUsageAccumulator.record(input, output);
    }

    static boolean isAuthFailure(Throwable t) {
        // Walk the exception cause chain looking for known auth indicators
        for (Throwable c = t; c != null; c = c.getCause()) {
            String name = c.getClass().getName().toLowerCase();
            String msg = c.getMessage() != null ? c.getMessage().toLowerCase() : "";
            if (name.contains("accessdenied")
                || name.contains("authorization")
                || name.contains("permissiondenied")
                || msg.contains("access denied")
                || msg.contains("not authorized")
                || msg.contains("403")) {
                return true;
            }
        }
        return false;
    }

    static boolean isQuotaFailure(Throwable t) {
        // Walk the exception cause chain looking for known quota indicators
        for (Throwable c = t; c != null; c = c.getCause()) {
            String name = c.getClass().getName().toLowerCase();
            String msg = c.getMessage() != null ? c.getMessage().toLowerCase() : "";
            if (name.contains("resourceexhausted")
                || name.contains("throttling")
                || msg.contains("quota")
                || msg.contains("throttl")
                || msg.contains("rate exceeded")
                || msg.contains("429")) {
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
}
