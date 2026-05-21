package com.avaulta.gateway.rules.augments;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.SimpleTokenizer;

import java.io.InputStream;
import java.util.*;

/**
 * ALPHA: Proof of concept NLP Processor.
 * Lazily loads OpenNLP models and performs sentence structure extraction.
 */
public class SentenceMetadataProcessor {

    private static volatile SentenceDetectorME sentenceDetector;
    private static volatile POSTaggerME posTagger;
    private static volatile ChunkerME chunker;
    private static volatile DictionaryLemmatizer lemmatizer;
    
    private static final Object lock = new Object();

    // Closed-class lists for signal derivation
    private static final Set<String> HEDGE_WORDS = Set.of("maybe", "perhaps", "kind", "sort", "probably", "somewhat", "possibly");
    private static final Set<String> CONSTRAINT_WORDS = Set.of("must", "only", "never", "always", "don't", "avoid", "require", "cannot");

    /**
     * Initializes models lazily from classpath or remote streams.
     * For this PoC, loads from classpath resources.
     */
    private static void initializeModels() {
        if (sentenceDetector == null) {
            synchronized (lock) {
                if (sentenceDetector == null) {
                    try (InputStream sentenceModelStream = loadStream("/opennlp/en-sent.bin");
                         InputStream posModelStream = loadStream("/opennlp/en-pos-maxent.bin");
                         InputStream chunkerModelStream = loadStream("/opennlp/en-chunker.bin");
                         InputStream lemmatizerStream = loadStream("/opennlp/en-lemmatizer.dict")) {
                        SentenceDetectorME localSentenceDetector = new SentenceDetectorME(new SentenceModel(sentenceModelStream));
                        POSTaggerME localPosTagger = new POSTaggerME(new POSModel(posModelStream));
                        ChunkerME localChunker = new ChunkerME(new ChunkerModel(chunkerModelStream));
                        DictionaryLemmatizer localLemmatizer = new DictionaryLemmatizer(lemmatizerStream);

                        sentenceDetector = localSentenceDetector;
                        posTagger = localPosTagger;
                        chunker = localChunker;
                        lemmatizer = localLemmatizer;
                    } catch (Exception e) {
                        // For PoC, ignore if models are missing, we will handle nulls
                        System.err.println("Warning: NLP models not found. NLP Augment will return empty data. " + e.getMessage());
                    }
                }
            }
        }
    }

    private static InputStream loadStream(String path) {
        InputStream is = SentenceMetadataProcessor.class.getResourceAsStream(path);
        if (is == null) {
            throw new RuntimeException("Model file not found in classpath: " + path);
        }
        return is;
    }

    public static Map<String, Object> process(String text, Map<String, List<String>> taxonomy) {
        initializeModels();
        if (sentenceDetector == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new TreeMap<>();
        List<Map<String, Object>> sentencesOutput = new ArrayList<>();
        Map<String, Object> docSummary = new TreeMap<>();

        String[] sentences = sentenceDetector.sentDetect(text);
        int totalTokens = 0;
        Map<String, Integer> sentenceTypes = new TreeMap<>();
        Set<String> allNounCategories = new HashSet<>();
        int totalSuppressedCommon = 0;
        int totalSuppressedProper = 0;
        boolean anyHedged = false;
        boolean anyConstraint = false;
        boolean anyQuestion = false;
        boolean anyNegated = false;

        // Invert taxonomy for fast lookup: word -> category
        Map<String, String> wordToCategory = new HashMap<>();
        if (taxonomy != null) {
            for (Map.Entry<String, List<String>> entry : taxonomy.entrySet()) {
                String cat = entry.getKey();
                for (String word : entry.getValue()) {
                    wordToCategory.put(word.toLowerCase(), cat);
                }
            }
        }

        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];
            String[] tokens = SimpleTokenizer.INSTANCE.tokenize(sentence);
            totalTokens += tokens.length;

            String[] tags = posTagger.tag(tokens);
            String[] chunks = chunker.chunk(tokens, tags);

            Map<String, Object> sObj = new TreeMap<>();
            sObj.put("index", i);

            List<Map<String, Object>> verbsList = new ArrayList<>();
            List<Map<String, Object>> nounsList = new ArrayList<>();
            List<String> modifiersList = new ArrayList<>();

            int vpCount = 0;
            int npCount = 0;
            boolean isQuestion = false;
            boolean isImperative = false;
            boolean sHedged = false;
            boolean sConstraint = false;
            boolean sNegated = false;
            boolean isPassive = false;

            int suppressedCommon = 0;
            int suppressedProper = 0;

            for (int j = 0; j < tokens.length; j++) {
                String token = tokens[j].toLowerCase();
                String tag = tags[j];
                String chunk = chunks[j];

                if (token.equals("?")) isQuestion = true;
                if (HEDGE_WORDS.contains(token)) sHedged = true;
                if (CONSTRAINT_WORDS.contains(token)) sConstraint = true;
                if (token.equals("not") || token.equals("n't")) sNegated = true;

                if (chunk.startsWith("B-VP")) vpCount++;
                if (chunk.startsWith("B-NP")) npCount++;

                if (j == 0 && tag.startsWith("VB")) {
                    isImperative = true;
                }

                // Passive voice approx: VBN (past participle) inside a VP
                if (tag.equals("VBN") && chunk.contains("VP")) {
                    isPassive = true;
                }

                // Verbs
                if (tag.startsWith("VB")) {
                    Map<String, Object> vObj = new TreeMap<>();
                    vObj.put("verb", tokens[j]);
                    vObj.put("pos", tag);
                    vObj.put("is_modal", tag.equals("MD"));
                    vObj.put("is_auxiliary", token.equals("be") || token.equals("is") || token.equals("are") || token.equals("have") || token.equals("has"));
                    // Approximate negation
                    vObj.put("is_negated", (j > 0 && (tokens[j-1].equalsIgnoreCase("not") || tokens[j-1].equalsIgnoreCase("n't"))));
                    verbsList.add(vObj);
                }

                // Modifiers
                if (tag.startsWith("JJ") || tag.startsWith("RB")) {
                    modifiersList.add(tokens[j]);
                }

                // Nouns
                if (tag.startsWith("NN")) {
                    if (tag.startsWith("NNP")) {
                        suppressedProper++;
                    } else {
                        String category = wordToCategory.get(token);
                        if (category != null) {
                            Map<String, Object> nObj = new TreeMap<>();
                            nObj.put("noun", tokens[j]);
                            nObj.put("category", category);
                            nObj.put("np_head", (j == tokens.length - 1 || !chunks[j+1].equals("I-NP")));
                            nObj.put("np_position", vpCount > 0 ? "object" : "subject");
                            nounsList.add(nObj);
                            allNounCategories.add(category);
                        } else {
                            suppressedCommon++;
                        }
                    }
                }
            }

            String sType = isQuestion ? "interrogative" : (isImperative ? "imperative" : "declarative");
            sentenceTypes.put(sType, sentenceTypes.getOrDefault(sType, 0) + 1);

            anyHedged |= sHedged;
            anyConstraint |= sConstraint;
            anyQuestion |= isQuestion;
            anyNegated |= sNegated;
            totalSuppressedCommon += suppressedCommon;
            totalSuppressedProper += suppressedProper;

            sObj.put("type", sType);
            sObj.put("verbs", verbsList);
            sObj.put("nouns", nounsList);
            sObj.put("modifiers", modifiersList);
            
            Map<String, Object> structure = new TreeMap<>();
            structure.put("voice", isPassive ? "passive" : "active");
            structure.put("vp_count", vpCount);
            structure.put("np_count", npCount);
            sObj.put("structure", structure);

            Map<String, Object> signals = new TreeMap<>();
            signals.put("hedged", sHedged);
            signals.put("constraint", sConstraint);
            signals.put("question", isQuestion);
            signals.put("negated", sNegated);
            sObj.put("signals", signals);

            Map<String, Integer> suppressed = new TreeMap<>();
            suppressed.put("common_nouns", suppressedCommon);
            suppressed.put("proper_nouns", suppressedProper);
            sObj.put("suppressed", suppressed);

            sentencesOutput.add(sObj);
        }

        result.put("sentences", sentencesOutput);

        docSummary.put("sentence_count", sentences.length);
        docSummary.put("token_count", totalTokens);
        docSummary.put("sentence_types", sentenceTypes);
        docSummary.put("noun_categories", new ArrayList<>(allNounCategories));
        
        Map<String, Integer> sumSuppressed = new TreeMap<>();
        sumSuppressed.put("common_nouns", totalSuppressedCommon);
        sumSuppressed.put("proper_nouns", totalSuppressedProper);
        docSummary.put("suppressed", sumSuppressed);

        docSummary.put("any_hedged", anyHedged);
        docSummary.put("any_constraint", anyConstraint);
        docSummary.put("any_question", anyQuestion);
        docSummary.put("any_negated", anyNegated);

        result.put("doc_summary", docSummary);

        return result;
    }
}
