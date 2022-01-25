package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import com.google.api.client.http.HttpContent;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NoArgsConstructor(onConstructor_ = @Inject)
public class ClientCredentialsGrantTokenRequestPayloadBuilder
        implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestPayloadBuilder {

    enum ConfigProperty implements ConfigService.ConfigProperty {
        PRIVATE_KEY_ID,
        PRIVATE_KEY,
        RESOURCE,
    }

    // 'client_credentials' is MSFT
    //for Google, this is "urn:ietf:params:oauth:grant-type:jwt-bearer"
    @Getter(onMethod_ = @Override)
    private final String grantType = "client_credentials";

    //for Google, this is "assertion"
    private static final String PARAM_CLIENT_ASSERTION = "client_assertion";


    private static final String PARAM_CLIENT_ASSERTION_TYPE = "client_assertion_type";
    private static final String CLIENT_ASSERTION_TYPE_JWT = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    //extra param MSFT uses on all its OAuth exchanges
    private static final String PARAM_RESOURCE = "resource";

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

    @SneakyThrows
    public HttpContent buildPayload() {

        String tokenEndpoint =
            config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT);

        String oauthClientId = config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID);

        JsonWebSignature.Header header = new JsonWebSignature.Header();
        header.setAlgorithm("RS256");
        header.setX509Thumbprint(config.getConfigPropertyOrError(ConfigProperty.PRIVATE_KEY_ID));
        //header.setType("JWT"); //this IS a JWT, but MSFT examples don't include this

        JsonWebToken.Payload payload = new JsonWebToken.Payload();
        Instant currentTime = clock.instant();

        payload.setAudience(tokenEndpoint);

        payload.setIssuer(oauthClientId);
        payload.setSubject(oauthClientId);

        payload.setJwtId(uuidGenerator.get().toString()); // MSFT docs say a random GUID, which is what they call UUID

        payload.setIssuedAtTimeSeconds(currentTime.getEpochSecond());
        payload.setExpirationTimeSeconds(currentTime.plus(DEFAULT_EXPIRATION_DURATION).getEpochSecond());

        Map<String, String> data = new HashMap<>();

        data.put("grant_type", getGrantType());

        String assertion = JsonWebSignature.signUsingRsaSha256(
            getServiceAccountPrivateKey(), jsonFactory, header, payload);

        data.put(PARAM_CLIENT_ASSERTION, assertion);
        data.put("client_id", oauthClientId);
        data.put(PARAM_CLIENT_ASSERTION_TYPE, CLIENT_ASSERTION_TYPE_JWT);

        //msft-specific thing
        config.getConfigPropertyAsOptional(ConfigProperty.RESOURCE)
            .ifPresent(r -> data.put(PARAM_RESOURCE, r));

        return new UrlEncodedContent(data);

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
        Exception unexpectedException = null;
        try {
            KeyFactory keyFactory = SecurityUtils.getRsaKeyFactory();
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            return privateKey;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
           throw new IOException("Unexpected exception reading PKCS data", exception);
        }
    }
}
