package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import lombok.Builder;
import lombok.Value;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.util.Base64;

/*
 * encodes legacy pseudonyms as tokens, starting with "t~" followed by hash value (presumed to be
 * already base64 encoded in legacy case; eg v0.3)
 *
 * considered using a distinct prefix ("l~") for legacy, but instead have made
 * UrlSafeTokenPseudonymEncoder able to decode these as well; and the difference between this legacy
 * encoding and url safe is implicit - only legacy can contain a `.`; anything NOT containing a '.'
 * is either urlsafetoken, or a case where legacy is equivalent to urlsafetoken. in all cases,
 * the UrlSafeTokenPseudonymEncoder can decode them.
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
        Parts parts = parse(pseudonym);
        builder.domain(parts.getDomain());
        builder.hash(decodeHash(parts.getHash()));
        return builder.build();
    }

    byte[] decodeHash(String encodedHash) {
        return decoder.decode(StringUtils.replaceChars(encodedHash, "_.", "/+"));
    }

    @Override
    public boolean canBeDecoded(String possiblePseudonym) {
        return possiblePseudonym != null
            && possiblePseudonym.startsWith(TOKEN_PREFIX)
            && parse(possiblePseudonym).getHash().length() == 43;
    }

    private Parts parse(String encodedPseudonym) {
        Parts.PartsBuilder builder = Parts.builder();
        if (encodedPseudonym.contains(DOMAIN_SEPARATOR)) {
            builder.domain(encodedPseudonym.substring(encodedPseudonym.indexOf(DOMAIN_SEPARATOR) + 1));
            encodedPseudonym = encodedPseudonym.substring(0, encodedPseudonym.indexOf(DOMAIN_SEPARATOR));
        }

        String hash =  encodedPseudonym.startsWith(TOKEN_PREFIX) ? encodedPseudonym.substring(TOKEN_PREFIX.length()) : encodedPseudonym;
        builder.hash(hash);

        return builder.build();
    }

    @Builder
    @Value
    static class Parts {
        String domain;

        @Builder.Default
        String hash = "";
    }
}
