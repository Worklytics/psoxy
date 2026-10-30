package com.avaulta.gateway.rules.augments;

import java.util.List;

/**
 * Closed-set classify scored by the in-function decider. One rule is one choice question.
 * The loopback URL and the server's field names stay behind this interface.
 */
public interface EmbeddedDecideClassify {

    /**
     * @return the chosen label, or {@code null} when the attempt produced no label and should be
     * retried. A down loopback process throws {@link GenMetadataAugmentException} with
     * {@link GenMetadataAugmentException.Code#UNAVAILABLE}.
     */
    GenMetadataInferenceResult classify(String taskPrompt, List<String> classes, String inputData,
                                        GenMetadataInferenceOptions options);
}
