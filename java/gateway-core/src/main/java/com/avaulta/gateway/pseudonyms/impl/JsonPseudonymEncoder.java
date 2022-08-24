package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

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

