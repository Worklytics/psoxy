package co.worklytics.psoxy.gateway;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public enum ApiModeConfigProperty implements ConfigService.ConfigProperty {
    /**
     * if provided, requests to proxy with `Prefer: respond-async` header will be processed asynchronously and responses output to the target
     * ONLY applicable in API Data Connector mode ONLY
     */
    ASYNC_OUTPUT_DESTINATION,


    /**
     * identifies the SourceAuthStrategy to use when connecting to the data source API
     */
    SOURCE_AUTH_STRATEGY_IDENTIFIER,

    /**
     * control the TLS protocol version used by proxy for outbound connections (eg, to data source)
     * OPTIONAL; default to 'TLSv1.3'
     * only safe alternative setting would be 'TLSv1.2'; we provide option to configure this in
     * case there is some API supported by proxy that doesn't support TLSv1.3 (we're not aware of
     * any as of Sept 2024)
     */
    TLS_VERSION,

    /**
     * target 'Host' to forward requests to, in HTTP sense
     */
    TARGET_HOST,
    ;


    /**
     * possible values for TLS_VERSION config property
     *
     */
    public static class TlsVersions {
        public final static String TLSv1_2 = "TLSv1.2";
        public final static String TLSv1_3 = "TLSv1.3";
        public final  static String[] ALL = {TLSv1_2, TLSv1_3};
    }
}
