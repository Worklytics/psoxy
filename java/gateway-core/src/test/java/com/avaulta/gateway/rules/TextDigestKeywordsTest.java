package com.avaulta.gateway.rules;

import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.transforms.Transform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * keywords in both flavors of text digest ({@link Augment.TextDigest}, and the deprecated
 * {@link Transform.TextDigest}) are matched by {@link KeywordMatcher}
 */
class TextDigestKeywordsTest {

    static final List<String> KEYWORDS = List.of("pull request", "code review", "dall-e", "write");

    static final String TEXT = "Write a Code Review of my pull request, then ask DALL-E for a logo.";

    static final Map<String, Integer> EXPECTED = Map.of("pull request", 1, "code review", 1, "dall-e", 1, "write", 1);

    @Test
    void augment_phraseKeywords() {
        Augment.TextDigest augment = Augment.TextDigest.builder().keywords(KEYWORDS).build();

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) augment.compute(TEXT);

        assertEquals(EXPECTED, result.get("keywords"));
        assertEquals(TEXT.length(), result.get("length"));
    }

    @Test
    void transform_phraseKeywords() {
        Transform.TextDigest transform = Transform.TextDigest.builder().keywords(KEYWORDS).build();

        TreeMap<String, Object> result = transform.generate(TEXT);

        assertEquals(EXPECTED, result.get("keywords"));
        assertEquals(TEXT.length(), result.get("length"));
    }

    @Test
    void noKeywordMatched_noKeywordsProperty() {
        Augment.TextDigest augment = Augment.TextDigest.builder().keywords(KEYWORDS).build();
        Transform.TextDigest transform = Transform.TextDigest.builder().keywords(KEYWORDS).build();

        assertFalse(augment.generate("nothing to see here").containsKey("keywords"));
        assertFalse(transform.generate("nothing to see here").containsKey("keywords"));
    }

    @Test
    void noKeywordsConfigured_noKeywordsProperty() {
        Augment.TextDigest augment = Augment.TextDigest.builder().build();
        Transform.TextDigest transform = Transform.TextDigest.builder().build();

        assertEquals(Map.of("length", 4, "word_count", 1), augment.generate("word"));
        assertEquals(Map.of("length", 4, "word_count", 1), transform.generate("word"));
    }

    @Test
    void augment_equalityNotAffectedByUse() {
        Augment.TextDigest used = Augment.TextDigest.builder().keywords(KEYWORDS).build();
        Augment.TextDigest unused = Augment.TextDigest.builder().keywords(KEYWORDS).build();

        used.compute(TEXT);

        assertEquals(unused, used);
        assertEquals(unused.hashCode(), used.hashCode());
    }

    @Test
    void sharedInstance_concurrentFirstUse() throws Exception {
        int threads = 8;
        for (int round = 0; round < 50; round++) {
            Augment.TextDigest augment = Augment.TextDigest.builder().keywords(KEYWORDS).build();
            Transform.TextDigest transform = Transform.TextDigest.builder().keywords(KEYWORDS).build();

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                List<Callable<Object>> tasks = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    tasks.add(() -> augment.generate(TEXT).get("keywords"));
                    tasks.add(() -> transform.generate(TEXT).get("keywords"));
                }
                for (Future<Object> future : executor.invokeAll(tasks)) {
                    assertEquals(EXPECTED, future.get());
                }
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }
}
