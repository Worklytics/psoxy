package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * implementation of defacto encoding used by BulkDataSanitizerImpl as of v0.4.30
 */
public class Sha256PseudonymEncoder implements PseudonymEncoder {


    @Override
    public String encode(Pseudonym pseudonym) {
        return base64Encode(pseudonym.getHash());
    }

    @Override
    public Pseudonym decode(String input) {
        return Pseudonym.builder()
            .hash(input.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    @Override
    public boolean canBeDecoded(String possiblePseudonym) {
        return possiblePseudonym != null &&
            possiblePseudonym.getBytes(StandardCharsets.UTF_8).length == 43; //43 rather than 32, bc of base64 encoding
    }

    //base64 encoding, to match implementation in HashUtils.java from psoxy-core v0.4.30
    String base64Encode(byte[] bytes) {
        String encoded = new String(
            Base64.getEncoder()
                .withoutPadding()
                .encode(bytes),
            StandardCharsets.UTF_8);
        return StringUtils.replaceChars(encoded, "/+", "_.");
    }
}
