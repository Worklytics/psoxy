package com.avaulta.gateway.pseudonyms;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PseudonymImplementation {

    //not based on scope; base64-url encoded
    DEFAULT("v0.4"),
    //includes 'scope'
    LEGACY("v0.3"),
    ;

    @Getter @NonNull
    private final String httpHeaderValue;


    public static PseudonymImplementation parseHttpHeaderValue(String httpHeaderValue) {
        for (PseudonymImplementation impl : values()) {
            if (impl.getHttpHeaderValue().equals(httpHeaderValue)) {
                return impl;
            }
        }
        throw new IllegalArgumentException("Unknown pseudonym implementation: " + httpHeaderValue);
    }
}
