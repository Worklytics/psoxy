package co.worklytics.psoxy;


import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * headers that control how Psoxy works
 *
 * anything passed as headers like this shouldn't have info-sec implications.
 */
@RequiredArgsConstructor
public enum ControlHeader {

    /**
     * @see co.worklytics.psoxy.impl.PseudonymImplementations
     */
    PSEUDONYM_IMPLEMENTATION("Pseudonym-Implementation"),

    /**
     * this header - sent with any value - means the request is a health check, not actually
     * intended to be forwarded to source
     */
    HEALTH_CHECK("Health-Check"),

    /**
     *  if sent to proxy, no response body needs to be returned to client.
     *  (eg, just write response to side output(s), if any)
     *
     *  3 modes of this?
     *    - response required
     *    - response optional (let proxy decide, potentially returning a reference to response object that will be writing asynchronously to a side-output)
     *    - no response wanted
     *
     *  optional case may be interesting optimization; process inline when expedient (eg, small response), but if appears
     *  expensive, or if server has already taken a long time to respond - such that proxy is unlikely to sanitize response within
     *  http timeout - then return a reference to the response object ASAP, and let the client poll for completion.
     *
     *
     *  better as 'side output only' or something? does this make sense if a side output isn't configured?
     */
    NO_RESPONSE_BODY("No-Response-Body"),

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
     */
    USER_TO_IMPERSONATE("User-To-Impersonate"),
    ;

    @NonNull
    final String httpNamePart;

    public String getHttpHeader() {
        return "X-Psoxy-" + httpNamePart;
    }
}
