package co.worklytics.psoxy.impl.gen;

import lombok.Value;

import java.util.Map;

/**
 * One parsed choice answer from the v1 decider server. {@code label} is null when the body parsed
 * but {@code choice} was absent.
 */
@Value
public class EmbeddedDecideChoice {

    String label;
    Double confidence;
    Map<String, Double> probabilities;
    boolean usagePresent;
    int inputTokens;
    int outputTokens;
}
