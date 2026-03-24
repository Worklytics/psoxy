package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;

import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//NOTE: coupled to fixed-length hash function
public class UrlSafeTokenPseudonymEncoder implements PseudonymEncoder {



    /**
     * URL-safe prefix to put in front of reversible pseudonyms that use SHA-256 hash
     *
     * q: make configurable, to support compatibility with various REST-API clients??
     *
     * alternatives:
     *   - prefix + suffix to make stronger
     *
     */
    public static final String REVERSIBLE_PREFIX = "p~";
    public static final String TOKEN_PREFIX = "t~";
    public static final String ENCRYPTED_PREFIX = "e~"; // q: just use `p~` for this? it's just more general than pseudonym and specifies handling more clea
    public static final String HASH_PREFIX = "h~"; // q: just use `t~` for this? or don't bother in this case?

    //length of base64-url-encoded IV + ciphertext
    static final int REVERSIBLE_PSEUDONYM_LENGTH_WITHOUT_PREFIX = 43;
    public static final String DOMAIN_SEPARATOR = "@";


    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();

    // Match the reversible token prefix literally, eg `p~` + 43 chars of base64url.
    // Then match the base64url body of the reversible token.
    // The length is at least the encoded reversible payload length.
    static final String REVERSIBLE_PSEUDONYM_REGEX =
        Pattern.quote(REVERSIBLE_PREFIX) + "[a-zA-Z0-9_-]{"
            + REVERSIBLE_PSEUDONYM_LENGTH_WITHOUT_PREFIX + ",}";

    // Match only the reversible pseudonym token itself.
    public static final Pattern REVERSIBLE_PSEUDONYM_PATTERN =
        Pattern.compile(REVERSIBLE_PSEUDONYM_REGEX);

    // group 1: reversible pseudonym
    // group 2: optional domain, if any (emails have it, but domain is included in encoded)
    public static final Pattern REVERSIBLE_PSEUDONYM_WITH_OPTIONAL_DOMAIN_PATTERN =
        Pattern.compile(
            // Capture the reversible pseudonym token as group 1.
            "(" + REVERSIBLE_PSEUDONYM_REGEX + ")"
                // Capture an optional email-style domain suffix as group 2.
                // This is the trailing `@domain.tld` portion when present.
                + "((?:@[A-Za-z0-9.-]+)?)");

    @Override
    public String encode(Pseudonym pseudonym) {
        String encoded;
        if (pseudonym.getReversible() == null) {
            encoded = TOKEN_PREFIX + encoder.encodeToString(pseudonym.getHash());
        } else {
            if (!Arrays.equals(pseudonym.getHash(), 0, pseudonym.getHash().length, pseudonym.getReversible(), 0, pseudonym.getHash().length)) {
                throw new IllegalArgumentException("hash must be first part of reversible pseudonym");
            }

            encoded = REVERSIBLE_PREFIX +  encoder.encodeToString(pseudonym.getReversible());
        }
        if (pseudonym.getDomain() != null) {
            //q: url-encode DOMAIN_SEPARATOR?
            encoded += DOMAIN_SEPARATOR + pseudonym.getDomain();
        }
        return encoded;
    }

    @Override
    public Pseudonym decode(String pseudonym) {
        Pseudonym.PseudonymBuilder builder = Pseudonym.builder();

        String encodedPseudonym = null;
        if (pseudonym.contains(DOMAIN_SEPARATOR)) {
            encodedPseudonym = pseudonym.substring(0, pseudonym.indexOf(DOMAIN_SEPARATOR));
            builder.domain(pseudonym.substring(pseudonym.indexOf(DOMAIN_SEPARATOR) + 1));
        } else {
            encodedPseudonym = pseudonym;
        }

        if (encodedPseudonym.startsWith(REVERSIBLE_PREFIX)) {
            byte[] decoded = decoder.decode(encodedPseudonym.substring(REVERSIBLE_PREFIX.length()));
            builder.reversible(decoded);
            builder.hash(Arrays.copyOfRange(decoded, 0, Sha256DeterministicTokenizationStrategy.HASH_SIZE_BYTES));
        } else if (encodedPseudonym.startsWith(TOKEN_PREFIX)) {
            builder.hash(decoder.decode(encodedPseudonym.substring(TOKEN_PREFIX.length())));
        } else {
            //legacy case - ever used/needed?
            builder.hash(decoder.decode(encodedPseudonym));
        }

        return builder.build();
    }

    @Override
    public boolean canBeDecoded(String possiblePseudonym) {
        return possiblePseudonym != null &&
            (possiblePseudonym.startsWith(REVERSIBLE_PREFIX)
                || possiblePseudonym.startsWith(TOKEN_PREFIX));
    }

    /**
     * returns string after reversing all keyed pseudonyms created with this
     * PseudonymizationStrategy that it contains (if any)
     *
     * @param containsKeyedPseudonyms string that may contain keyed pseudonyms
     * @param reidentifier             function to reverse keyed pseudonym
     * @return string with all keyed pseudonyms it contains, if any, reversed to originals
     */
    public String decodeAndReverseAllContainedKeyedPseudonyms(String containsKeyedPseudonyms,
                                                              ReversibleTokenizationStrategy reidentifier) throws AESReversibleTokenizationStrategy.InvalidTokenException {
        return REVERSIBLE_PSEUDONYM_WITH_OPTIONAL_DOMAIN_PATTERN.matcher(containsKeyedPseudonyms).replaceAll(m -> {
            String keyedPseudonym = m.group(1);

            //q: if this fails, just return 'm.group()' as-is?? to consider possibility that pattern matched
            // something it shouldn't
            String original = reidentifier.getOriginalDatum(this.decode(keyedPseudonym).getReversible());

            //quote replacement, otherwise Matcher seems to treat it is a regex with potential
            // backreferences or something like that
            return Matcher.quoteReplacement(original);
        });
    }

}
