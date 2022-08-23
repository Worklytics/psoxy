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
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("e")
    byte[] encrypted;

    /**
     * cryptographic hash of canonicalized identifier
     */
    @JsonProperty("h")
    byte[] hash;
}
