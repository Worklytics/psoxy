package com.avaulta.gateway.pseudonyms;

import java.util.function.Function;

public interface PseudonymEncoder {


    String encode(Pseudonym pseudonym);

    Pseudonym decode(String pseudonym);

    /**
     * returns string after reversing all keyed pseudonyms created with this
     * PseudonymizationStrategy that it contains (if any)
     *
     * @param containsKeyedPseudonyms string that may contain keyed pseudonyms
     * @param reidentifier             function to reverse keyed pseudonym
     * @return string with all keyed pseudonyms it contains, if any, reversed to originals
     */
    String decodeAndReverseAllContainedKeyedPseudonyms(String containsKeyedPseudonyms, PseudonymizationStrategy reidentifier);
}
