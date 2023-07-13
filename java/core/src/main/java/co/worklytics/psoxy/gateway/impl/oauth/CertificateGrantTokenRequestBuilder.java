package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.util.PemReader;
import com.google.api.client.util.SecurityUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * implementation of an Access Token Request (per RFC6749 4.4.2) authenticated by an assertion (RFC 7521)
 * based on private key id + private key secret for building the JWT assertion to use
 * <p>
 * see
 * - <a href="https://datatracker.ietf.org/doc/html/rfc7521">...</a>
 * - <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2">...</a>
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class CertificateGrantTokenRequestBuilder
        implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder, RequiresConfiguration {

    enum ConfigProperty implements ConfigService.ConfigProperty {
        PRIVATE_KEY
    }

    @Getter(onMethod_ = @Override)
    private final String grantType = "certificate_credentials";

    //the jwt assertion
    final Duration DEFAULT_EXPIRATION_DURATION = Duration.ofMinutes(5);

    @Inject
    ConfigService config;
    @Inject
    JsonFactory jsonFactory;
    @Inject
    Clock clock;
    @Inject
    Provider<UUID> uuidGenerator;

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
                OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT, //should be redundant with strategy, but nonetheless req'd here too
                OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID,
                ConfigProperty.PRIVATE_KEY);
    }

    @Override
    public void addHeaders(HttpHeaders httpHeaders) {
        String oauthClientId = config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID);
        String tokenEndpoint =
                config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT);

        httpHeaders.setAuthorization("Bearer " + buildJwtAssertion(oauthClientId, tokenEndpoint));
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ConfigProperty.values());
    }

    @SneakyThrows
    public HttpContent buildPayload() {
        return new UrlEncodedContent(Collections.emptyList());
    }

    /**
     * build JWT assertion to authenticate client based on config
     *
     * @param clientId both subject + issuer of token
     * @param audience for the token (the endpoint/service being called)
     * @return JWT assertion as a string
     */
    @SneakyThrows
    private String buildJwtAssertion(String clientId, String audience) {
        JsonWebSignature.Header header = new JsonWebSignature.Header();
        header.setType("JWT");
        header.setAlgorithm("RS256");

        JsonWebToken.Payload payload = new JsonWebToken.Payload();
        Instant currentTime = clock.instant();

        payload.setAudience(audience);

        payload.setIssuer(clientId);
        payload.setSubject(clientId);

        payload.setJwtId(uuidGenerator.get().toString());

        payload.setIssuedAtTimeSeconds(currentTime.getEpochSecond());
        payload.setExpirationTimeSeconds(currentTime.plus(DEFAULT_EXPIRATION_DURATION).getEpochSecond());

        return JsonWebSignature.signUsingRsaSha256(
                getServiceAccountPrivateKey(), jsonFactory, header, payload);
    }

    private PrivateKey getServiceAccountPrivateKey() throws IOException {
        String privateKeyPem = config.getConfigPropertyOrError(ConfigProperty.PRIVATE_KEY);
        Reader reader = new StringReader(privateKeyPem);
        PemReader.Section section = PemReader.readFirstSectionAndClose(reader, "PRIVATE KEY");
        if (section == null) {
            throw new IOException("Invalid PKCS8 data.");
        }
        byte[] bytes = section.getBase64DecodedBytes();
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
        try {
            KeyFactory keyFactory = SecurityUtils.getRsaKeyFactory();
            return keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IOException("Unexpected exception reading PKCS data", exception);
        }
    }
}