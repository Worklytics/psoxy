package com.avaulta.gateway.rules.augments;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Structured output of the {@code sentenceMetadata} augment.
 */
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SentenceMetadataResult {

    @Singular
    List<Sentence> sentences;

    @JsonProperty("doc_summary")
    DocSummary docSummary;

    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Sentence {

        int index;
        String type;

        @Singular
        List<Verb> verbs;

        @Singular
        List<Noun> nouns;

        @Singular
        List<String> modifiers;

        Structure structure;
        Signals signals;
        SuppressedCounts suppressed;
    }

    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Verb {

        String verb;
        String pos;

        @JsonProperty("is_modal")
        boolean modal;

        @JsonProperty("is_auxiliary")
        boolean auxiliary;

        @JsonProperty("is_negated")
        boolean negated;
    }

    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Noun {

        String noun;
        String category;

        @JsonProperty("np_head")
        boolean npHead;

        @JsonProperty("np_position")
        String npPosition;
    }

    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Structure {

        String voice;

        @JsonProperty("vp_count")
        int vpCount;

        @JsonProperty("np_count")
        int npCount;
    }

    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Signals {

        boolean hedged;
        boolean constraint;
        boolean question;
        boolean negated;
    }

    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SuppressedCounts {

        @JsonProperty("common_nouns")
        int commonNouns;

        @JsonProperty("proper_nouns")
        int properNouns;
    }

    @NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocSummary {

        @JsonProperty("sentence_count")
        int sentenceCount;

        @JsonProperty("token_count")
        int tokenCount;

        @JsonProperty("sentence_types")
        Map<String, Integer> sentenceTypes;

        @JsonProperty("noun_categories")
        List<String> nounCategories;

        SuppressedCounts suppressed;

        @JsonProperty("any_hedged")
        boolean anyHedged;

        @JsonProperty("any_constraint")
        boolean anyConstraint;

        @JsonProperty("any_question")
        boolean anyQuestion;

        @JsonProperty("any_negated")
        boolean anyNegated;
    }
}
