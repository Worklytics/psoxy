package co.worklytics.psoxy;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * headers that control how Psoxy works
 *
 * anything passed as headers like this shouldn't have info-sec implications.
 *
 * NOTE: all of these are applicable ONLY in API Data Connector mode.
 */
@RequiredArgsConstructor
public enum ControlHeader {

    /**
     * alternative way to send authorization information to proxy instances
     *
     * as of v0.5.3, use-case if limited to Webhook-Collector mode
     *
     */
    AUTHORIZATION("Authorization"),

    /**
     * this header - sent with any value - means the request is a health check, not actually
     * intended to be forwarded to source
     */
    HEALTH_CHECK("Health-Check"),


    /**
     * @see co.worklytics.psoxy.impl.PseudonymImplementations
     */
    PSEUDONYM_IMPLEMENTATION("Pseudonym-Implementation"),

    /**
     * whether to skip sanitizer (for testing purposes, to obtain unsanitized baseline to compare
     *  with sanitized output)
     * this is respected ONLY if env var SKIP_SANITIZER is also set:
     * @see co.worklytics.psoxy.gateway.ProxyConfigProperty.SKIP_SANITIZER
     */
    SKIP_SANITIZER("Skip-Sanitizer"),

    /**
     * which user to impersonate when calling Source API
     *
     * q: specific to Google? generalizable??
     *
     * this is a header, but NOT something we forward to the source API. rather used
     */
    USER_TO_IMPERSONATE("User-To-Impersonate"),

    /**
     * Override the HTTP request path used for rule matching and upstream URL construction.
     * When present (and valid), the proxy treats the request as if it were sent to this path
     * and ignores the actual request path.
     *
     * <p>Value is used as-is (no base64 encoding). Must be a path beginning with {@code /}.
     *
     * <p>NOTE: when combined with inbound path-prefix trimming ({@code REQUEST_PATH_PREFIX_TO_TRIM}),
     * a present TargetPath should be used exactly — not run through prefix stripping — because it
     * already expresses the logical path for rules and upstream calls.
     */
    TARGET_PATH("TargetPath"),

    /**
     * Override the HTTP request query string used for rule matching and upstream URL construction.
     * When present (and valid), the proxy treats the request as if it were sent with this query
     * string and ignores any actual query string on the request.
     *
     * <p>Value should be the raw query string without a leading {@code ?} (a leading {@code ?}
     * is stripped if present). An empty value means "no query string".
     */
    TARGET_QUERY("TargetQuery"),
    ;

    @NonNull
    final String httpNamePart;

    public String getHttpHeader() {
        return "X-Psoxy-" + httpNamePart;
    }
}
