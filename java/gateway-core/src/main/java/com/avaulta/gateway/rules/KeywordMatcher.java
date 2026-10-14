package com.avaulta.gateway.rules;

import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts occurrences of keywords, including multi-word phrases, in a text.
 *
 * <p>Matching is case-insensitive and on whole words: a keyword must appear as written, with no
 * letter, digit or underscore (in any script, so accented letters count) directly before or after
 * it. Eg, {@code "pull request"} matches "Pull Request", {@code "dall-e"} matches "DALL-E",
 * {@code "how do"} does not match "how does", and {@code "code"} does not match "Codeänderung".
 * Same rule as Worklytics' own keyword matching, so counts agree whether or not data passes
 * through the proxy.
 *
 * <p>Each keyword is counted on its own, so overlapping keywords ({@code "explain"} and
 * {@code "explain how"}) both count. Counts are reported under the keyword, lowercased.
 *
 * <p>Thread-safety: immutable after construction.
 */
public class KeywordMatcher {

    /** pattern for each keyword, by keyword (lowercased) */
    private final Map<String, Pattern> patterns;

    /**
     * @param keywords to match; null and blank entries are ignored
     */
    public KeywordMatcher(Collection<String> keywords) {
        Map<String, Pattern> byKeyword = new LinkedHashMap<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword == null) {
                    continue;
                }
                String lower = keyword.strip().toLowerCase(Locale.ROOT);
                if (!lower.isEmpty()) {
                    byKeyword.putIfAbsent(lower,
                        Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(lower) + "(?![\\p{L}\\p{N}_])"));
                }
            }
        }
        this.patterns = Map.copyOf(byKeyword);
    }

    /**
     * @param text to search
     * @return occurrences of each keyword found, by keyword, sorted by keyword; empty if none
     */
    public TreeMap<String, Integer> count(String text) {
        TreeMap<String, Integer> counts = new TreeMap<>();
        if (StringUtils.isEmpty(text)) {
            return counts;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        patterns.forEach((keyword, pattern) -> {
            Matcher m = pattern.matcher(lower);
            int n = 0;
            while (m.find()) {
                n++;
            }
            if (n > 0) {
                counts.put(keyword, n);
            }
        });
        return counts;
    }
}
