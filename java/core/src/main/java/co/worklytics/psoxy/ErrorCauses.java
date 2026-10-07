package co.worklytics.psoxy;

/**
 * Enumeration of errors that cause a not 200 OK response from Psoxy
 * Values of response header {@see co.worklytics.psoxy.ResponseHeader.ERROR}
 */
public enum ErrorCauses {

    /**
     * Third party call returned error, that is not obviously something that falls under
     * 'CONNECTION_SETUP' case
     * eg - not obvious to use that its an authentication or authorization issue
     */
    API_ERROR,

    /**
     * some error dispatching request to an async handler
     */
    ASYNC_HANDLER_DISPATCH,

    /**
     * Sanitization rules blocked the call
     */
    BLOCKED_BY_RULES,

    /**
     * Credentials construction failing, or some authorization step not completed
     * could be authentication - eg a problem with proxy instances credentials
     * or authorization - eg proxy is authenticated with source, but lacks authorization to access
     * requested data
     */
    CONNECTION_SETUP,

    /**
     * failed to get configuration data; or misconfigured.
     */
    CONFIGURATION_FAILURE,
    /**
     * indicates failure to connect from proxy instance to source
     */
    CONNECTION_TO_SOURCE,

    /**
     * failed to build target URL (eg, that of source) from request URL (requested from proxy)
     */
    FAILED_TO_BUILD_URL,

    /**
     * network egress from proxy instance is blocked, likely due to VPC/serverless connector misconfiguration
     * or network connectivity issues (firewall, routing, etc)
     */
    NETWORK_EGRESS_BLOCKED,

    /**
     * the proxy could not reach its configuration store (AWS SSM Parameter Store or Secrets Manager).
     * On Lambda, this is usually a VPC without a reachable interface endpoint or NAT gateway for that
     * service, or a security group that blocks HTTPS to it. Distinct from {@link #NETWORK_EGRESS_BLOCKED},
     * which is an ambiguous timeout talking to the data source API.
     */
    CONFIG_STORE_UNREACHABLE,

    /**
     * the proxy could not connect to some service it depends on, and the failure was not identified
     * as the configuration store. The response and logs include the underlying client error so the
     * service can be identified from that message rather than from this code.
     */
    DEPENDENT_SERVICE_UNREACHABLE,

    /**
     * timed out waiting for a response from the source API after the connection was established
     */
    SOURCE_API_READ_TIMEOUT,

    /**
     * tokenized request parameter is invalid. most likely too stale.
     *
     * clients should re-try with a fresh token(s), which may involve re-fetching from the endpoint from which they originally got the tokenized parameter value
     */
    TOKENIZED_REQUEST_PARAMETER_INVALID,

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

    INVALID_REQUEST,

    /**
     * Client IP is not allowed by application-level lockdown rules.
     */
    UNAUTHORIZED_IP_ADDRESS,
    
    /**
     * Rules YAML is invalid or malformed
     */
    RULES_INVALID_YAML,
    
    /**
     * Rules don't match expected schema/pojo structure
     */
    RULES_INVALID,
    ;
}
