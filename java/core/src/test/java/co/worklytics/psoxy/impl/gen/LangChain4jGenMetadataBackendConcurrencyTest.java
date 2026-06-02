package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.resources.ResourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies single-flight model load behavior (without requiring a real model file).
 */
class LangChain4jGenMetadataBackendConcurrencyTest {

    @Test
    void resolveModel_singleFlightOnConcurrentMiss() throws InterruptedException {
        AtomicInteger loadAttempts = new AtomicInteger();
        ResourceService resourceService = objectPath -> {
            loadAttempts.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return java.util.Optional.empty();
        };

        GenMetadataConfig config = GenMetadataConfig.builder()
            .backend(GenMetadataConfig.BACKEND_LOCAL)
            .modelId("test-model")
            .timeoutSeconds(5)
            .maxInputChars(1024)
            .maxTokens(64)
            .build();

        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, new ObjectMapper(), new GenMetadataPromptBudget(new ObjectMapper()),
            new GenMetadataChatModelFactory(resourceService));

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

        assertEquals(1, loadAttempts.get());
    }
}
