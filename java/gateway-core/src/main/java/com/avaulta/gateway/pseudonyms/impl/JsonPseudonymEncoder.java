package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.ReversiblePseudonymStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.NotImplementedException;

@AllArgsConstructor
public class JsonPseudonymEncoder implements PseudonymEncoder {

    ObjectMapper objectMapper;

    @SneakyThrows
    @Override
    public String encode(Pseudonym pseudonym) {
        return objectMapper.writeValueAsString(pseudonym);
    }

    @SneakyThrows
    @Override
    public Pseudonym decode(String pseudonym) {
        return objectMapper.readerFor(Pseudonym.class).readValue(pseudonym);
    }
}

