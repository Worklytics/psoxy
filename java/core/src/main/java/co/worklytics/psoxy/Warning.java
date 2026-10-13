package co.worklytics.psoxy;

public enum Warning {

    COMPRESSION_NOT_REQUESTED,

    AUGMENT_OUTPUT_SCHEMA_MISMATCH,
    AUGMENT_GEN_INFERENCE_FAILED,
    AUGMENT_GEN_UNAVAILABLE,
    AUGMENT_CONFLICT_SKIPPED,
    ;

    public String asHttpHeaderCode() {
        return this.name().toLowerCase().replace('_', '-');
    }

    public static Warning parseHttpHeaderCode(String headerCode) {
        return Warning.valueOf(headerCode.toUpperCase().replace('-', '_'));
    }
}
