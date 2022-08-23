package com.avaulta.gateway.pseudonyms;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
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
     * deterministically encrypted; based on configuration, may be reversible for some period
     *
     * NOTE: should not be expected to be cryptographically secure; while difficult to reverse
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("e")
    byte[] encrypted;

    /**
     * SHA-256 hash of canonicalized identifier
     */
    @JsonProperty("h")
    byte[] hash;
}
