package co.worklytics.psoxy;


/**
 * Enumeration of errors that cause a not 200 OK response from Psoxy
 * Values of response header {@see co.worklytics.psoxy.ResponseHeader.ERROR}
 */
public enum ErrorCauses {

    /**
     * Third party call returned error, that is not obviously something that falls under 'CONNECTION_SETUP' case
     * eg - not obvious to use that its an authentication or authorization issue
     */
    API_ERROR,

    /**
     *  some error dispatching request to an async handler
     */
    ASYNC_HANDLER_DISPATCH,

    /**
     * Sanitization rules blocked the call
     */
    BLOCKED_BY_RULES,

    /**
     * Credentials construction failing, or some authorization step not completed
     *  could be authentication  - eg a problem with proxy instances credentials
     *  or authorization - eg  proxy is authenticated with source, but lacks authorization to access requested data
     */
    CONNECTION_SETUP,

    /**
     *  failed to get configuration data; or misconfigured.
     */
    CONFIGURATION_FAILURE,
    /**
     *  indicates failure to connect from proxy instance to source
     */
    CONNECTION_TO_SOURCE,

    /**
     *  failed to build target URL (eg, that of source) from request URL (requested from proxy)
     */
    FAILED_TO_BUILD_URL,

    /**
     * request was not sent over HTTPS
     */
    HTTPS_REQUIRED,

    /**
     *  failed to write to side output (response may have otherwise been successful)
     *  clients can ignore this if they choose.
     */
    SIDE_OUTPUT_FAILURE_SANITIZED,

    /**
     *  failed to write to side output, for original response (not sanitized)
     *  clients can ignore this if they choose.
     */
    SIDE_OUTPUT_FAILURE_ORIGINAL,

    /**
     *  An error internal to proxy's application logic, but not handled such that it could be mapped into one of the above.
     *  eg, something very unexpected, or a bug in the code.
     */
    UNKNOWN,
    ;
}
