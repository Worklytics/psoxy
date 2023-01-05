package com.avaulta.gateway.pseudonyms;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PseudonymImplementation {


    //NOTE: use a version id (v0.3, etc) NOT the enum name, as the enum name --> version number
    // is convention, to clarify what's default or not


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

    public static PseudonymImplementation parseConfigPropertyValue(String configPropertyValue) {
        for (PseudonymImplementation impl : values()) {
            if (impl.getHttpHeaderValue().equals(configPropertyValue)) {
                return impl;
            }
        }
        throw new IllegalArgumentException("Unknown pseudonym implementation: " + configPropertyValue);
    }
}
