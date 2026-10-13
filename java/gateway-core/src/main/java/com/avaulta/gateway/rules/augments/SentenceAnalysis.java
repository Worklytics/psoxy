package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.augments.TextMetadataResult.Sentence;

import java.util.Set;

/**
 * Intermediate result from {@link TextMetadataProcessor#analyzeSentence}.
 */
record SentenceAnalysis(Sentence sentence,
                        String sentenceType,
                        int tokenCount,
                        int suppressedCommon,
                        int suppressedProper,
                        boolean hedged,
                        boolean constraint,
                        boolean question,
                        boolean negated,
                        Set<String> nounCategories) {
}
