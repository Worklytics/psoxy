package co.worklytics.psoxy.impl;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PseudonymImplementation {
    //current
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
