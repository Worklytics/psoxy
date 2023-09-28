package com.avaulta.gateway.tokens;

import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.util.Arrays;

/**
 * a token that can serve as a deterministic surrogate for original data
 *
 */
@Getter
@EqualsAndHashCode(callSuper = false)
@SuperBuilder(toBuilder = true)
public class Token {
    /**
     * potentially reversible form of this token; if passed back to TokenizationStrategy
     * instance that created it, that instance may, based on its configuration(rules) be able to
     * reverse it and use it as a request parameter for some period
     *
     * (in effect, it's deterministically encrypted, based on configuration; but should not be
     * expected to be cryptographically secure encryption - we make no such claim, although in
     * practice strive to implement it as such)
     *
     * prefix of this, of length HASH_SIZE_BYTES, MUST be equivalent to `hash` value for
     * token.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("r")
    byte[] reversible;

    /**
     * hash of canonicalized token; usually SHA-256
     */
    @JsonProperty("h")
    byte[] hash;

    public byte[] getHash() {
        if (this.hash == null) {
            //legacy case,
            return Arrays.copyOfRange(this.getReversible(), 0, Sha256DeterministicTokenizationStrategy.HASH_SIZE_BYTES);
        } else {
            return this.hash;
        }
    }
}
