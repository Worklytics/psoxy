package co.worklytics.psoxy.gateway.output;


import co.worklytics.psoxy.gateway.HostEnvironment;
import lombok.AccessLevel;
import lombok.Getter;

public interface OutputLocation {


    // NOT ideal to have all potential implementations of OutputLocation defined here ...
    enum LocationKind {
        S3("s3://"),
        GCS("gs://"),
        SQS("https://sqs"),
        PUBSUB("https://pubsub"),
        ;

        @Getter(AccessLevel.PACKAGE)
        private final String uriPrefix;

        LocationKind(String uriPrefix) {
            this.uriPrefix = uriPrefix;
        }
    }
    /**
     * kind of location, e.g. "s3", "gcs", etc
     *
     * @return
     */
    LocationKind getKind();

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
            .anyMatch(protocol -> protocol.equalsIgnoreCase(this.getKind().name()));
    }
}
