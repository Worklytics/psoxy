package com.avaulta.gateway.pseudonyms;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * a pseudonym that can serve as a deterministic surrogate for an identifier
 *
 */
@Value
@Builder(toBuilder = true)
public class Pseudonym {

    //TODO: really a property of the hash alg
    public static final int HASH_SIZE_BYTES = 32; //SHA-256


    /**
     * a 'domain' for the identifier, which may be returned plain or may itself be tokenized
     *
     * q: is this a specific type of metadata about the identifier?
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("d")
    String domain;

    /**
     * potentially reversible form of this pseudonym; if passed back to PseudonymizationStrategy
     * instance that created it, that instance may, based on its configuration(rules) be able to
     * reverse it and use it as a request parameter for some period
     *
     * (in effect, it's deterministically encrypted, based on configuration; but should not be
     * expected to be cryptographically secure encryption - we make no such claim, although in
     * practice strive to implement it as such)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("r")
    byte[] reversible;

    /**
     * SHA-256 hash of canonicalized identifier
     */
    @JsonProperty("h")
    byte[] hash;
}
