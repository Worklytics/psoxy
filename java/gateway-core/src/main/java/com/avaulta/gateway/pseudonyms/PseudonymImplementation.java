package com.avaulta.gateway.pseudonyms;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@RequiredArgsConstructor
public enum PseudonymImplementation {


    //NOTE: use a version id (v0.3, etc) NOT the enum name, as the enum name --> version number
    // is convention, to clarify what's default or not


    //not based on scope; base64-url encoded
    DEFAULT("v0.4"),
    //includes 'scope'
    @Deprecated // configuring this should blow up proxy ops, rather than filling nonsense data
    LEGACY("v0.3"),
    ;

    @Getter @NonNull
    private final String httpHeaderValue;

    public static PseudonymImplementation parseHttpHeaderValue(String httpHeaderValue) {
        return Arrays.stream(values())
            .filter( p -> p.getHttpHeaderValue().equals(httpHeaderValue))
            .findFirst()
            .orElseThrow( () -> new IllegalArgumentException("Unknown pseudonym implementation: " + httpHeaderValue));
    }

    public static PseudonymImplementation parseConfigPropertyValue(String configPropertyValue) {
        return parseHttpHeaderValue(configPropertyValue);
    }
}
