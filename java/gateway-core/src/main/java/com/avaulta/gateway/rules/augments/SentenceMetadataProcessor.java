package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.resources.ResourceService;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.DocSummary;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.Noun;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.Sentence;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.Signals;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.Structure;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.SuppressedCounts;
import com.avaulta.gateway.rules.augments.SentenceMetadataResult.Verb;
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
 * ALPHA: Proof of concept NLP processor for the {@code sentenceMetadata} augment.
 * Lazily loads OpenNLP models from a {@link ResourceService} and performs sentence structure extraction.
 *
 * <p>Thread-safe for concurrent requests: model fields are published only after full initialization.
 */
public class SentenceMetadataProcessor {

    private static final Logger log = Logger.getLogger(SentenceMetadataProcessor.class.getName());

    static final String MODEL_PATH_PREFIX = "opennlp/";

    private final ResourceService resourceService;

    private volatile SentenceDetectorME sentenceDetector;
    private volatile POSTaggerME posTagger;
    private volatile ChunkerME chunker;

    private final Object lock = new Object();

    public SentenceMetadataProcessor(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * Compute sentence metadata for a single augment invocation.
     */
    public SentenceMetadataResult compute(Augment.SentenceMetadata augment, Object input) {
        if (!(input instanceof String text) || text.isEmpty()) {
            return null;
        }
        return process(
            text,
            augment.getTaxonomy(),
            Augment.SentenceMetadata.signalWords(augment.getHedgeWords(), Augment.SentenceMetadata.DEFAULT_HEDGE_WORDS),
            Augment.SentenceMetadata.signalWords(augment.getConstraintWords(), Augment.SentenceMetadata.DEFAULT_CONSTRAINT_WORDS));
    }

    public SentenceMetadataResult process(String text,
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
            String[] tags = posTagger.tag(tokens);
            String[] chunks = chunker.chunk(tokens, tags);

            SentenceAnalysis analysis = analyzeSentence(
                i, tokens, tags, chunks, wordToCategory, hedgeWords, constraintWords);

            totalTokens += analysis.tokenCount();
            totalSuppressedCommon += analysis.suppressedCommon();
            totalSuppressedProper += analysis.suppressedProper();
            anyHedged |= analysis.hedged();
            anyConstraint |= analysis.constraint();
            anyQuestion |= analysis.question();
            anyNegated |= analysis.negated();
            sentenceTypes.merge(analysis.sentenceType(), 1, Integer::sum);
            analysis.nounCategories().forEach(allNounCategories::add);
            sentencesOutput.add(analysis.sentence());
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

    private Map<String, String> invertTaxonomy(Map<String, List<String>> taxonomy) {
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

    /**
     * Derive sentence metadata from pre-tokenized NLP output. Package-visible for deterministic
     * unit tests without loading OpenNLP model binaries.
     */
    static SentenceAnalysis analyzeSentence(int index,
                                            String[] tokens,
                                            String[] tags,
                                            String[] chunks,
                                            Map<String, String> wordToCategory,
                                            Set<String> hedgeWords,
                                            Set<String> constraintWords) {
        List<Verb> verbsList = new ArrayList<>();
        List<Noun> nounsList = new ArrayList<>();
        List<String> modifiersList = new ArrayList<>();
        Set<String> nounCategories = new HashSet<>();

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
                        nounCategories.add(category);
                    } else {
                        suppressedCommon++;
                    }
                }
            }
        }

        String sentenceType = isQuestion ? "interrogative" : (isImperative ? "imperative" : "declarative");

        Sentence sentence = Sentence.builder()
            .index(index)
            .type(sentenceType)
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
            .build();

        return new SentenceAnalysis(
            sentence,
            sentenceType,
            tokens.length,
            suppressedCommon,
            suppressedProper,
            sHedged,
            sConstraint,
            isQuestion,
            sNegated,
            Set.copyOf(nounCategories));
    }

    private void initializeModels() {
        if (sentenceDetector != null) {
            return;
        }
        synchronized (lock) {
            if (sentenceDetector != null) {
                return;
            }
            try (InputStream sentenceModelStream = loadModel("en-sent.bin");
                 InputStream posModelStream = loadModel("en-pos-maxent.bin");
                 InputStream chunkerModelStream = loadModel("en-chunker.bin")) {
                SentenceDetectorME detector = new SentenceDetectorME(new SentenceModel(sentenceModelStream));
                POSTaggerME tagger = new POSTaggerME(new POSModel(posModelStream));
                ChunkerME chunkerModel = new ChunkerME(new ChunkerModel(chunkerModelStream));
                sentenceDetector = detector;
                posTagger = tagger;
                chunker = chunkerModel;
            } catch (Exception e) {
                log.log(Level.INFO, "OpenNLP models not available; sentenceMetadata augment unavailable", e);
            }
        }
    }

    private InputStream loadModel(String modelFileName) {
        String objectPath = MODEL_PATH_PREFIX + modelFileName;
        return resourceService.getResource(objectPath)
            .orElseThrow(() -> new IllegalStateException("OpenNLP model not found: " + objectPath));
    }
}
