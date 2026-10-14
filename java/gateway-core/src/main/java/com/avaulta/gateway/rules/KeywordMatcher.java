package com.avaulta.gateway.rules;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Counts occurrences of keywords, including multi-word phrases, in a text.
 *
 * <p>Text and keywords are cut into words with the same rule: lowercase, then split at each run of
 * non-word characters ({@code \W+}). So a keyword matches whole words only, regardless of case,
 * spacing or punctuation:
 * <ul>
 *   <li>{@code "pull request"} matches "Pull  Request" and "pull\nrequest"</li>
 *   <li>{@code "dall-e"} matches "DALL-E" (both are the words {@code dall, e})</li>
 *   <li>{@code "how do"} does not match "how does"</li>
 * </ul>
 *
 * <p>Each keyword is counted on its own, so keywords that overlap ({@code "explain"} and
 * {@code "explain how"}) both count. Counts are reported under the keyword as configured, lowercased.
 *
 * <p>Thread-safety: immutable after construction, so one instance can be shared across threads.
 */
public class KeywordMatcher {

    private static final Pattern NON_WORD = Pattern.compile("\\W+");

    /**
     * keywords as configured (lowercased), by their words joined with a single space; eg,
     * {@code "dall e"} for the keyword {@code "dall-e"}
     */
    private final Map<String, String> keywordsByWords;

    /**
     * first word of each keyword; words that no keyword starts with are skipped without lookups
     */
    private final Set<String> firstWords;

    /**
     * number of words in the longest keyword
     */
    private final int maxWords;

    /**
     * @param keywords to match; null, blank, and punctuation-only entries are ignored. If several
     *                 entries have the same words (eg, "Email" and "email"), the first is used.
     */
    public KeywordMatcher(Collection<String> keywords) {
        Map<String, String> byWords = new HashMap<>();
        Set<String> first = new HashSet<>();
        int longest = 0;
        if (keywords != null) {
            for (String keyword : keywords) {
                List<String> words = tokenize(keyword);
                if (!words.isEmpty()) {
                    byWords.putIfAbsent(String.join(" ", words), keyword.toLowerCase(Locale.ROOT));
                    first.add(words.get(0));
                    longest = Math.max(longest, words.size());
                }
            }
        }
        this.keywordsByWords = Map.copyOf(byWords);
        this.firstWords = Set.copyOf(first);
        this.maxWords = longest;
    }

    /**
     * @param text to search
     * @return occurrences of each keyword found, by keyword, sorted by keyword; empty if none
     */
    public TreeMap<String, Integer> count(String text) {
        TreeMap<String, Integer> counts = new TreeMap<>();
        if (keywordsByWords.isEmpty() || StringUtils.isEmpty(text)) {
            return counts;
        }
        List<String> words = tokenize(text);
        for (int start = 0; start < words.size(); start++) {
            if (!firstWords.contains(words.get(start))) {
                continue;
            }
            int lastEnd = Math.min(start + maxWords, words.size());
            for (int end = start + 1; end <= lastEnd; end++) {
                String keyword = keywordsByWords.get(String.join(" ", words.subList(start, end)));
                if (keyword != null) {
                    counts.merge(keyword, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private List<String> tokenize(String text) {
        if (text == null) {
            return List.of();
        }
        return NON_WORD.splitAsStream(text.toLowerCase(Locale.ROOT))
            .filter(word -> !word.isEmpty())
            .toList();
    }
}
