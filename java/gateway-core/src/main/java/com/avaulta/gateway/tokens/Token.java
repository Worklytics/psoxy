package com.avaulta.gateway.tokens;

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

    //NOTE: really a property of hash alg, which is SHA-256
    public static final int HASH_SIZE_BYTES = 32;

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
     * SHA-256 hash of canonicalized token
     */
    @JsonProperty("h")
    byte[] hash;

    public byte[] getHash() {
        if (reversible == null) {
            return hash;
        } else {
            //kinda hacky; puts restriction on implementations of TokenizationStrategy (reversibles
            // MUST use SHA-256 as prefix of their results)
            return Arrays.copyOfRange(reversible, 0, HASH_SIZE_BYTES);
        }
    }
}
