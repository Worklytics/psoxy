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
    public static final String JAVA_SOURCE_CODE_VERSION = "rc-v0.6.3";

    /**
     * a random UUID used to salt the hash of the salt.  Purpose of this is to invalidate any non-purpose built rainbow table solution.
     *   (Eg, if we just directly hashed the salt, a general rainbow table of hashes could be used to determine the salt value)
     *
     *  That said, if salt is 20+ random characters, there is no *general* rainbow table of that length in existence and one is impossible to
     *  build, as storing it requires ~10e25 petabytes - which is about 10e20 more storage than humanity actually has. So this additional
     *  protection isn't so necessary, but whatever.
     *
     *  do NOT change this value. if you do, we won't be able to detect that proxy-side salts of changed.
     */
    public static final String SALT_FOR_SALT = "f33c366c-ae91-4819-b221-f9794ebb8145";

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