package co.worklytics.psoxy;

/**
 * Enumeration of errors that cause a not 200 OK response from Psoxy
 * Values of response header {@see co.worklytics.psoxy.ResponseHeader.ERROR}
 */
public enum ErrorCauses {

    /**
     * Sanitization rules blocked the call
     */
    BLOCKED_BY_RULES,

    /**
     * Credentials construction failing
     */
    CONNECTION_SETUP,
    /**
     * Third party call returned error
     */
    API_ERROR,

    /**
     * request was not sent over HTTPS
     */
    HTTPS_REQUIRED,

    /**
     *  indicates failure to connect from proxy instance to source
     */
    CONNECTION_TO_SOURCE,

    /**
     *  failed to build target URL (eg, that of source) from request URL (requested from proxy)
     */
    FAILED_TO_BUILD_URL,

    /**
     *  failed to get configuration data; or misconfigured.
     */
    CONFIGURATION_FAILURE,
    ;

}
