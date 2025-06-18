package co.worklytics.psoxy.gateway;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public enum ApiModeConfigProperty implements ConfigService.ConfigProperty {
    SOURCE_AUTH_STRATEGY_IDENTIFIER, //target API endpoint to forward request to
    /**
     * control the TLS protocol version used by proxy for outbound connections (eg, to data source)
     * OPTIONAL; default to 'TLSv1.3'
     * only safe alternative setting would be 'TLSv1.2'; we provide option to configure this in
     * case there is some API supported by proxy that doesn't support TLSv1.3 (we're not aware of
     * any as of Sept 2024)
     */
    TLS_VERSION,
    TARGET_HOST,
    ;


    public static class TlsVersions {
        public final static String TLSv1_2 = "TLSv1.2";
        public final static String TLSv1_3 = "TLSv1.3";
        public final  static String[] ALL = {TLSv1_2, TLSv1_3};
    }
}
