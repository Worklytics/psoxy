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
     * which user to impersonate when calling Source API
     *
     * q: specific to Google? generalizable??
     */
    USER_TO_IMPERSONATE("User-To-Impersonate"),
    /**
     * this header - sent with any value - means the request is a health check, not actually
     * intended to be forwarded to source
     */
    HEALTH_CHECK("Health-Check"),
    ;

    @NonNull
    final String httpNamePart;

    public String getHttpHeader() {
        return "X-Psoxy-" + httpNamePart;
    }
}
