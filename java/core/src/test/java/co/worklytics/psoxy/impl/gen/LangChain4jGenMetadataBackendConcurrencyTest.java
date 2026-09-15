package co.worklytics.psoxy.impl.gen;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies single-flight client init for cloud providers ({@code computeIfAbsent}).
 */
class LangChain4jGenMetadataBackendConcurrencyTest {

    @Test
    void resolveModel_singleFlightOnConcurrentMiss() throws InterruptedException {
        AtomicInteger createAttempts = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder().build();
            }
        };        GenMetadataChatModelProvider provider = new GenMetadataChatModelProvider() {
            @Override
            public boolean supports(GenMetadataConfig config) {
                return GenMetadataConfig.BACKEND_BEDROCK.equalsIgnoreCase(config.getBackend());
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                createAttempts.incrementAndGet();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return model;
            }
        };

        GenMetadataConfig config = GenMetadataConfig.builder()
            .backend(GenMetadataConfig.BACKEND_BEDROCK)
            .modelId("test-model")
            .timeoutSeconds(5)
            .maxInputChars(1024)
            .maxTokens(64)
            .build();

        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, new ObjectMapper(), new GenMetadataPromptBudget(new ObjectMapper()),
            new GenMetadataChatModelFactory(Set.of(provider)));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    backend.resolveModel();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, createAttempts.get());
    }
}
