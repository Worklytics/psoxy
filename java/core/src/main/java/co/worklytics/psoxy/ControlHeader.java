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
     * client does NOT want a response; proxy should ACK request with 204 No Content, continue API call
     * in background, then write response to side output (assuming it's enabled)
     */
    PROCESS_ASYNC("Process-Async"),

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
    ;

    @NonNull
    final String httpNamePart;

    public String getHttpHeader() {
        return "X-Psoxy-" + httpNamePart;
    }
}
