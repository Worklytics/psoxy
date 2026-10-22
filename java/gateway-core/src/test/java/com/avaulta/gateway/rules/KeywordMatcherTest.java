package com.avaulta.gateway.rules;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordMatcherTest {

    @Test
    void count_singleWords_caseInsensitive() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("eMail", "write", "Summarize", "not_present"));

        assertEquals(Map.of("email", 2, "write", 2, "summarize", 1),
            matcher.count("Please WRITE an Email and summarize the results of the write operation. Don't email me again!"));
    }

    @Test
    void count_phrase() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("pull request", "code review"));

        assertEquals(Map.of("pull request", 1, "code review", 1),
            matcher.count("Can you do a Code Review of my pull request?"));
    }

    @Test
    void count_phraseWithHyphen() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("dall-e", "e-mail"));

        assertEquals(Map.of("dall-e", 1, "e-mail", 1),
            matcher.count("Ask DALL-E for a logo, then e-mail it."));
    }

    @Test
    void count_phrase_ignoresCase() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("Pull Request"));

        assertEquals(Map.of("pull request", 3),
            matcher.count("PULL REQUEST, Pull Request and pull request"));
    }

    @Test
    void count_phrase_mustBeWrittenAsKeyword() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("pull request", "dall-e"));

        assertTrue(matcher.count("pull  request, pull\nrequest, pull-request, dall e").isEmpty());
    }

    @Test
    void count_accentedLetters_partOfWord() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("code", "bug", "café"));

        assertTrue(matcher.count("Die Codeänderung, le débug").isEmpty());
        assertEquals(Map.of("café", 1), matcher.count("Un CAFÉ"));
    }

    @Test
    void count_keywordWithSymbols() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("c++", "node.js"));

        assertEquals(Map.of("c++", 1, "node.js", 1), matcher.count("C++ and Node.js, not c or node"));
    }

    @Test
    void count_phrase_mustBeWholeWords() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("how do", "unit test"));

        assertTrue(matcher.count("how does this work? a unit tester").isEmpty());
        assertTrue(matcher.count("how").isEmpty());
        assertTrue(matcher.count("somehow do it").isEmpty());
    }

    @Test
    void count_phrase_wordsMustBeAdjacent() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("pull request"));

        assertTrue(matcher.count("pull the request").isEmpty());
        assertTrue(matcher.count("request pull").isEmpty());
    }

    @Test
    void count_phraseRepeated() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("what is"));

        assertEquals(Map.of("what is", 2), matcher.count("What is this? And what is that?"));
    }

    @Test
    void count_phraseAtEndOfText() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("pull request"));

        assertTrue(matcher.count("open a pull").isEmpty());
        assertEquals(Map.of("pull request", 1), matcher.count("open a pull request"));
    }

    @Test
    void count_overlappingKeywords_eachCountsOnItsOwn() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("explain", "explain how", "how"));

        assertEquals(Map.of("explain", 1, "explain how", 1, "how", 1),
            matcher.count("Explain how it works"));
    }

    @Test
    void count_resultSortedByKeyword() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("write", "bug", "code review"));

        assertEquals(List.of("bug", "code review", "write"),
            new ArrayList<>(matcher.count("write code review bug").keySet()));
    }

    @Test
    void count_duplicateKeywords_countedOnce() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("Email", "email"));

        assertEquals(Map.of("email", 1), matcher.count("email"));
    }

    @Test
    void count_blankKeywords_ignored() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList(null, "", "   ", "bug"));

        assertEquals(Map.of("bug", 1), matcher.count("a bug -- here"));
    }

    @Test
    void count_noKeywords() {
        assertTrue(new KeywordMatcher(List.of()).count("anything at all").isEmpty());
        assertTrue(new KeywordMatcher(null).count("anything at all").isEmpty());
    }

    @Test
    void count_emptyText() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("bug"));

        assertTrue(matcher.count(null).isEmpty());
        assertTrue(matcher.count("").isEmpty());
        assertTrue(matcher.count("?!").isEmpty());
    }

    @Test
    void count_textStartingWithPunctuation() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("bug"));

        assertEquals(Map.of("bug", 1), matcher.count("?? bug"));
    }

    @Test
    void count_reusable_sameResultEachTime() {
        KeywordMatcher matcher = new KeywordMatcher(List.of("pull request"));

        assertEquals(matcher.count("a pull request"), matcher.count("a pull request"));
    }
}
