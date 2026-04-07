package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * Application constants and computed runtime values for the proxy.
 *
 * Provided as an injectable singleton via Dagger, so that the user agent string
 * and version info are available everywhere without static references.
 */
@Value
@Builder
public class ProxyConstants {

    /**
     * Brand name of the proxy product.
     */
    public static final String PRODUCT_BRAND_NAME = "Psoxy";

    /**
     * Version of the Java source code.  Used to identify the version of the proxy.
     */
    public static final String JAVA_SOURCE_CODE_VERSION = "v0.6.0";

    /**
     * Java runtime version, captured at construction time.
     */
    @NonNull
    @Builder.Default
    String javaVersion = System.getProperty("java.version", "unknown");

    /**
     * User-Agent string to use on outbound HTTP requests to source APIs.
     *
     * Built from brand name, proxy version, and java version by default; can be
     * fully overridden via the USER_AGENT config property.
     */
    @NonNull
    String userAgent;

    /**
     * Builds the default user agent string from the static constants and the
     * current java version.
     *
     * @return default user agent string, e.g. "Psoxy/v0.6.0 (API Data Sanitization; Java/21)"
     */
    public static String buildDefaultUserAgent() {
        return PRODUCT_BRAND_NAME + "/"
            + JAVA_SOURCE_CODE_VERSION
            + " (API Data Sanitization; Java/"
            + System.getProperty("java.version", "unknown") + ")";
    }
}