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
     * Credentials construction failing, or some authorization step not completed
     *  could be authentication  - eg a problem with proxy instances credentials
     *  or authorization - eg  proxy is authenticated with source, but lacks authorization to access requested data
     */
    CONNECTION_SETUP,

    /**
     * Third party call returned error, that is not obviously something that falls under 'CONNECTION_SETUP' case
     * eg - not obvious to use that its an authentication or authorization issue
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

    /**
     *  An error internal to proxy's application logic, but not handled such that it could be mapped into one of the above.
     *  eg, something very unexpected, or a bug in the code.
     */
    UNKNOWN,
    ;

}
