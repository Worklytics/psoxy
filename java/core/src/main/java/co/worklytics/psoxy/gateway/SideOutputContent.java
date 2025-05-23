package co.worklytics.psoxy.gateway;

public enum SideOutputContent {

    /**
     * The original content, as received from the source.
     */
    ORIGINAL,

    /**
     * The content post-sanitization (processing/transformation)
     */
    SANITIZED,
    ;
}
