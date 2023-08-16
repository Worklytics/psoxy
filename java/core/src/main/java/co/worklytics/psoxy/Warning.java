package co.worklytics.psoxy;

public enum Warning {

    COMPRESSION_NOT_REQUESTED,
    ;

    public String asHttpHeaderCode() {
        return this.name().toLowerCase().replace('_', '-');
    }

    public static Warning parseHttpHeaderCode(String headerCode) {
        return Warning.valueOf(headerCode.toUpperCase().replace('-', '_'));
    }
}
