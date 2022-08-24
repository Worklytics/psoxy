package com.avaulta.gateway.pseudonyms;

import com.avaulta.gateway.tokens.Token;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * a pseudonym that can serve as a deterministic surrogate for an identifier
 *
 */
@Getter
@EqualsAndHashCode(callSuper = false)
@SuperBuilder(toBuilder = true)
public class Pseudonym extends Token {

    /**
     * a 'domain' for the identifier, which may be returned plain or may itself be tokenized
     *
     * q: is this a specific type of metadata about the identifier?
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("d")
    String domain;

}
