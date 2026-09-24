package co.worklytics.psoxy.impl.gen;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChain4jGenMetadataBackendTimeoutTest {

    @Test
    void chatWithTimeout_returnsNullWhenModelExceedsLimit() throws Exception {
        GenMetadataConfig config = BedrockGenMetadataConfig.of("test-model", 1);

        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of());
        ObjectMapper om = new ObjectMapper();
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, om, new GenMetadataPromptBudget(), factory,
            new GenMetadataTokenUsageAccumulator(),
            new GenMetadataPromptBuilder(om),
            new GenMetadataResponseFormats());

        ChatModel slowModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ChatResponse.builder().build();
            }
        };

        ChatResponse response = backend.chatWithTimeout(
            slowModel, List.of(UserMessage.from("hello")), null, 64);
        assertNull(response);
    }

    @Test
    void chatWithTimeout_timesOutWaitingForOccupiedSlots() throws Exception {
        GenMetadataConfig config = BedrockGenMetadataConfig.of("test-model", 1);

        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of());
        ObjectMapper om = new ObjectMapper();
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, om, new GenMetadataPromptBudget(), factory,
            new GenMetadataTokenUsageAccumulator(),
            new GenMetadataPromptBuilder(om),
            new GenMetadataResponseFormats());

        ChatModel occupying = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ChatResponse.builder().build();
            }
        };

        int callers = LangChain4jGenMetadataBackend.CLOUD_MAX_CONCURRENT + 1;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch done = new CountDownLatch(callers);
        AtomicInteger nulls = new AtomicInteger();
        long startedMs = System.currentTimeMillis();
        for (int i = 0; i < callers; i++) {
            pool.submit(() -> {
                try {
                    ChatResponse response = backend.chatWithTimeout(
                        occupying, List.of(UserMessage.from("hello")), null, 64);
                    if (response == null) {
                        nulls.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(4, TimeUnit.SECONDS));
        long elapsedMs = System.currentTimeMillis() - startedMs;
        assertEquals(callers, nulls.get());
        assertTrue(elapsedMs < 3_500, "should bound wait+inference instead of queueing; took "
            + elapsedMs + "ms");
        pool.shutdownNow();
    }
}
