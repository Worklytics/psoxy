package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.resources.ResourceService;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.DocSummary;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.Noun;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.Sentence;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.Signals;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.Structure;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.SuppressedCounts;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.Verb;
import lombok.experimental.UtilityClass;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.SimpleTokenizer;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ALPHA: Proof of concept NLP Processor.
 * Lazily loads OpenNLP models and performs sentence structure extraction.
 */
@UtilityClass
public class SentenceMetadataProcessor {

    private static final Logger log = Logger.getLogger(SentenceMetadataProcessor.class.getName());

    static final String MODEL_PATH_PREFIX = "opennlp/";

    private static volatile SentenceDetectorME sentenceDetector;
    private static volatile POSTaggerME posTagger;
    private static volatile ChunkerME chunker;

    private static volatile ResourceService resourceService;

    private static final Object lock = new Object();

    /**
     * Configure resource loading for OpenNLP models. Must be set before first use in production;
     * {@link ResourceService} implementations handle local vs remote resolution.
     */
    public static void configureResourceService(ResourceService service) {
        resourceService = service;
    }

    /**
     * Reset cached models and provider. For unit tests only.
     */
    static void resetForTests() {
        synchronized (lock) {
            sentenceDetector = null;
            posTagger = null;
            chunker = null;
            resourceService = null;
        }
    }

    public static SentenceMetadataResult process(String text,
                                                 Map<String, List<String>> taxonomy,
                                                 Set<String> hedgeWords,
                                                 Set<String> constraintWords) {
        initializeModels();
        if (sentenceDetector == null) {
            return null;
        }

        List<Sentence> sentencesOutput = new ArrayList<>();
        Map<String, Integer> sentenceTypes = new TreeMap<>();
        Set<String> allNounCategories = new HashSet<>();
        int totalTokens = 0;
        int totalSuppressedCommon = 0;
        int totalSuppressedProper = 0;
        boolean anyHedged = false;
        boolean anyConstraint = false;
        boolean anyQuestion = false;
        boolean anyNegated = false;

        Map<String, String> wordToCategory = invertTaxonomy(taxonomy);

        String[] sentences = sentenceDetector.sentDetect(text);
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];
            String[] tokens = SimpleTokenizer.INSTANCE.tokenize(sentence);
            totalTokens += tokens.length;

            String[] tags = posTagger.tag(tokens);
            String[] chunks = chunker.chunk(tokens, tags);

            List<Verb> verbsList = new ArrayList<>();
            List<Noun> nounsList = new ArrayList<>();
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

                if (token.equals("?")) {
                    isQuestion = true;
                }
                if (hedgeWords.contains(token)) {
                    sHedged = true;
                }
                if (constraintWords.contains(token)) {
                    sConstraint = true;
                }
                if (token.equals("not") || token.equals("n't")) {
                    sNegated = true;
                }

                if (chunk.startsWith("B-VP")) {
                    vpCount++;
                }
                if (chunk.startsWith("B-NP")) {
                    npCount++;
                }

                if (j == 0 && tag.startsWith("VB")) {
                    isImperative = true;
                }

                if (tag.equals("VBN") && chunk.contains("VP")) {
                    isPassive = true;
                }

                if (tag.startsWith("VB")) {
                    verbsList.add(Verb.builder()
                        .verb(tokens[j])
                        .pos(tag)
                        .modal(tag.equals("MD"))
                        .auxiliary(token.equals("be") || token.equals("is") || token.equals("are")
                            || token.equals("have") || token.equals("has"))
                        .negated(j > 0 && (tokens[j - 1].equalsIgnoreCase("not")
                            || tokens[j - 1].equalsIgnoreCase("n't")))
                        .build());
                }

                if (tag.startsWith("JJ") || tag.startsWith("RB")) {
                    modifiersList.add(tokens[j]);
                }

                if (tag.startsWith("NN")) {
                    if (tag.startsWith("NNP")) {
                        suppressedProper++;
                    } else {
                        String category = wordToCategory.get(token);
                        if (category != null) {
                            nounsList.add(Noun.builder()
                                .noun(tokens[j])
                                .category(category)
                                .npHead(j == tokens.length - 1 || !chunks[j + 1].equals("I-NP"))
                                .npPosition(vpCount > 0 ? "object" : "subject")
                                .build());
                            allNounCategories.add(category);
                        } else {
                            suppressedCommon++;
                        }
                    }
                }
            }

            String sType = isQuestion ? "interrogative" : (isImperative ? "imperative" : "declarative");
            sentenceTypes.merge(sType, 1, Integer::sum);

            anyHedged |= sHedged;
            anyConstraint |= sConstraint;
            anyQuestion |= isQuestion;
            anyNegated |= sNegated;
            totalSuppressedCommon += suppressedCommon;
            totalSuppressedProper += suppressedProper;

            sentencesOutput.add(Sentence.builder()
                .index(i)
                .type(sType)
                .verbs(verbsList)
                .nouns(nounsList)
                .modifiers(modifiersList)
                .structure(Structure.builder()
                    .voice(isPassive ? "passive" : "active")
                    .vpCount(vpCount)
                    .npCount(npCount)
                    .build())
                .signals(Signals.builder()
                    .hedged(sHedged)
                    .constraint(sConstraint)
                    .question(isQuestion)
                    .negated(sNegated)
                    .build())
                .suppressed(SuppressedCounts.builder()
                    .commonNouns(suppressedCommon)
                    .properNouns(suppressedProper)
                    .build())
                .build());
        }

        DocSummary docSummary = DocSummary.builder()
            .sentenceCount(sentences.length)
            .tokenCount(totalTokens)
            .sentenceTypes(sentenceTypes)
            .nounCategories(new ArrayList<>(allNounCategories))
            .suppressed(SuppressedCounts.builder()
                .commonNouns(totalSuppressedCommon)
                .properNouns(totalSuppressedProper)
                .build())
            .anyHedged(anyHedged)
            .anyConstraint(anyConstraint)
            .anyQuestion(anyQuestion)
            .anyNegated(anyNegated)
            .build();

        return SentenceMetadataResult.builder()
            .sentences(sentencesOutput)
            .docSummary(docSummary)
            .build();
    }

    private static Map<String, String> invertTaxonomy(Map<String, List<String>> taxonomy) {
        Map<String, String> wordToCategory = new HashMap<>();
        if (taxonomy != null) {
            for (Map.Entry<String, List<String>> entry : taxonomy.entrySet()) {
                String category = entry.getKey();
                for (String word : entry.getValue()) {
                    wordToCategory.put(word.toLowerCase(), category);
                }
            }
        }
        return wordToCategory;
    }

    private static void initializeModels() {
        if (sentenceDetector != null) {
            return;
        }
        synchronized (lock) {
            if (sentenceDetector != null) {
                return;
            }
            ResourceService service = resourceService;
            if (service == null) {
                log.log(Level.INFO, "OpenNLP resource service not configured; sentenceMetadata augment unavailable");
                return;
            }
            try (InputStream sentenceModelStream = loadModel(service, "en-sent.bin");
                 InputStream posModelStream = loadModel(service, "en-pos-maxent.bin");
                 InputStream chunkerModelStream = loadModel(service, "en-chunker.bin")) {
                sentenceDetector = new SentenceDetectorME(new SentenceModel(sentenceModelStream));
                posTagger = new POSTaggerME(new POSModel(posModelStream));
                chunker = new ChunkerME(new ChunkerModel(chunkerModelStream));
            } catch (Exception e) {
                log.log(Level.INFO, "OpenNLP models not available; sentenceMetadata augment unavailable", e);
            }
        }
    }

    private static InputStream loadModel(ResourceService service, String modelFileName) {
        String objectPath = MODEL_PATH_PREFIX + modelFileName;
        return service.getResource(objectPath)
            .orElseThrow(() -> new IllegalStateException("OpenNLP model not found: " + objectPath));
    }
}
