package co.worklytics.psoxy.gateway.impl.oauth;

import com.google.api.client.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Singleton
public class AuthUtils {

    /**
     * Sets Basic Auth header on given HttpHeaders
     */
    public static void setBasicAuthHeader(
        HttpHeaders headers,
        String clientId,
        String clientSecret) {
        String token = Base64.getEncoder()
            .encodeToString(String.join(":", clientId, clientSecret).getBytes(StandardCharsets.UTF_8));

        headers.setAuthorization("Basic " + token);
    }
}
