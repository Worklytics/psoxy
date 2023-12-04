package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.util.Base64;

/*
 * encodes legacy pseudonyms as tokens, starting with "l~" followed by hash value (presumed to be
 * already base64 encoded in legacy case; eg v0.3)
 *
 * NOTE: v0.4.38 to v0.4.41 may have pseudonyms encoded in this format, but with `t~` prefix, for
 * PseudonymizeRegexMatches cases
 *
 *
 */
@Log
public class LegacyPseudonymTokenEncoder implements PseudonymEncoder {

    // use
    public static final String TOKEN_PREFIX = "t~";
    public static final String DOMAIN_SEPARATOR = "@";

    //just for clarity, not actually used
    Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getDecoder();

    @Override
    public String encode(Pseudonym pseudonym) {
        if (pseudonym.getReversible() != null) {
            throw new IllegalArgumentException("cannot encode reversible pseudonym with LegacyPseudonymTokenEncoder");
        }

        String encoded = TOKEN_PREFIX + StringUtils.replaceChars(encoder.encodeToString(pseudonym.getHash()), "/+", "_.");

        if (pseudonym.getDomain() != null) {
            encoded += DOMAIN_SEPARATOR + pseudonym.getDomain();
        }
        return encoded;
    }


    // for completeness; UrlSafeTokenPseudonymEncoder can also decode these
    @Override
    public Pseudonym decode(String pseudonym) {
        Pseudonym.PseudonymBuilder builder = Pseudonym.builder();

        // trim and parse domain
        String encodedPseudonym = null;
        if (pseudonym.contains(DOMAIN_SEPARATOR)) {
            encodedPseudonym = pseudonym.substring(0, pseudonym.indexOf(DOMAIN_SEPARATOR));
            builder.domain(pseudonym.substring(pseudonym.indexOf(DOMAIN_SEPARATOR) + 1));
        } else {
            encodedPseudonym = pseudonym;
        }

        String encoded = encodedPseudonym.startsWith(TOKEN_PREFIX) ? encodedPseudonym.substring(TOKEN_PREFIX.length()) : encodedPseudonym;

        // see HashUtils
        builder.hash(decodeHash(encoded));

        return builder.build();
    }

    byte[] decodeHash(String encodedHash) {
        return decoder.decode(StringUtils.replaceChars(encodedHash, "_.", "/+"));
    }

    @Override
    public boolean canBeDecoded(String possiblePseudonym) {
        return possiblePseudonym != null
            && possiblePseudonym.startsWith(TOKEN_PREFIX);
    }
}
