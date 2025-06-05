package co.worklytics.psoxy.gateway.output;


import co.worklytics.psoxy.gateway.HostEnvironment;

public interface OutputLocation {

    /**
     * kind of location, e.g. "s3", "gcs", etc
     *
     * @return
     */
    String getKind();

    /**
     * URI of the output location, e.g. "s3://bucket/path", "gcs://bucket/path", etc.
     * @return
     */
    String getUri();


    /**
     * Check if the protocol of this output location is supported by the host environment.
     *
     * @param hostEnvironment the host environment to check against
     * @return true if the protocol is supported, false otherwise
     */
   default boolean isSupported(HostEnvironment hostEnvironment) {
        return hostEnvironment.getSupportedOutputKinds()
            .stream()
            .anyMatch(protocol -> protocol.equalsIgnoreCase(this.getKind()));
    }
}
