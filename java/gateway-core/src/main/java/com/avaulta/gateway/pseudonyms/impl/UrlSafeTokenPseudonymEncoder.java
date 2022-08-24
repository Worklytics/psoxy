package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//NOTE: coupled to fixed-length hash function
public class UrlSafeTokenPseudonymEncoder implements PseudonymEncoder {



    /**
     * URL-safe prefix to put in front of reversible pseudonyms
     *
     * q: make configurable, to support compatibility with various REST-API clients??
     *
     * alternatives:
     *   - prefix + suffix to make stronger
     *
     */
    static final String PREFIX = "p~";

    //length of base64-url-encoded IV + ciphertext
    static final int REVERSIBLE_PSEUDONYM_LENGTH_WITHOUT_PREFIX = 43;
    private static final String DOMAIN_SEPARATOR = "@";


    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();

    static final Pattern REVERSIBLE_PSEUDONYM_PATTERN =
        //Pattern.compile("p\\~[a-zA-Z0-9_-]{43}"); //not clear to me why this doesn't work
        Pattern.compile(Pattern.quote(PREFIX) + "[a-zA-Z0-9_-]{" + REVERSIBLE_PSEUDONYM_LENGTH_WITHOUT_PREFIX + ",}");

    @Override
    public String encode(Pseudonym pseudonym) {
        String encoded;
        if (pseudonym.getReversible() == null) {
            encoded = encoder.encodeToString(pseudonym.getHash());
        } else {
            encoded = PREFIX + encoder.encodeToString(pseudonym.getReversible());
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

        if (encodedPseudonym.startsWith(PREFIX)) {
            byte[] decoded = decoder.decode(encodedPseudonym.substring(PREFIX.length()));
            builder.reversible(decoded);
        } else {
            builder.hash(decoder.decode(encodedPseudonym));
        }

        return builder.build();
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
                                                              ReversibleTokenizationStrategy reidentifier) {
        return REVERSIBLE_PSEUDONYM_PATTERN.matcher(containsKeyedPseudonyms).replaceAll(m -> {
            String keyedPseudonym = m.group();

            //q: if this fails, just return 'm.group()' as-is?? to consider possibility that pattern matched
            // something it shouldn't
            String original = reidentifier.getOriginalDatum(this.decode(keyedPseudonym).getReversible());

            //quote replacement, otherwise Matcher seems to treat it is a regex with potential
            // backreferences or something like that
            return Matcher.quoteReplacement(original);
        });
    }

}
