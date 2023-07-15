package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.client.util.PemReader;
import com.google.api.client.util.SecurityUtils;
import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;

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
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * implementation of an Access Token Request (per RFC6749 4.4.2) authenticated by an assertion (RFC 7521)
 * based on private key id + private key secret for building the JWT assertion to use
 * <p>
 * see
 * - <a href="https://datatracker.ietf.org/doc/html/rfc7521">...</a>
 * - <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2">...</a>
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class ClientCredentialsGrantTokenRequestBuilder
        implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder, RequiresConfiguration {

    enum ConfigProperty implements ConfigService.ConfigProperty {
        PRIVATE_KEY_ID,
        PRIVATE_KEY,
        TOKEN_SCOPE,
        //NOTE: you should configure this as a secret in Secret Manager
        CLIENT_SECRET,
        // Define how the client credentials are set; by default is based on certificate to ensure compatibility with previous versions
        CREDENTIALS_FLOW
    }

    private enum CredentialFlowType {
        // https://www.rfc-editor.org/rfc/rfc6749
        CLIENT_SECRET,
        // https://www.rfc-editor.org/rfc/rfc7523
        JWT_ASSERTION
    }

    // 'client_credentials' is MSFT
    //for Google, this is "urn:ietf:params:oauth:grant-type:jwt-bearer"
    @Getter(onMethod_ = @Override)
    private final String grantType = "client_credentials";

    //for Google, this is "assertion"
    // see: https://datatracker.ietf.org/doc/html/rfc7521#section-4.2
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CLIENT_ASSERTION = "client_assertion";
    // see: https://datatracker.ietf.org/doc/html/rfc7521#section-4.2
    private static final String PARAM_CLIENT_ASSERTION_TYPE = "client_assertion_type";
    private static final String CLIENT_ASSERTION_TYPE_JWT = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    //https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2
    private static final String PARAM_SCOPE = "scope";
    private static final String PARAM_GRANT_TYPE = "grant_type";

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
        if (getCredentialsType() == CredentialFlowType.CLIENT_SECRET) {
            return Set.of(
                    OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID,
                    ConfigProperty.CLIENT_SECRET
            );
        } else {
            return Set.of(
                    OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT, //should be redundant with strategy, but nonetheless req'd here too
                    OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID,
                    ConfigProperty.PRIVATE_KEY_ID,
                    ConfigProperty.PRIVATE_KEY
            );
        }
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ConfigProperty.values());
    }

    @SneakyThrows
    public HttpContent buildPayload() {

        Map<String, String> data;
        if (getCredentialsType().equals(CredentialFlowType.CLIENT_SECRET)) {
            data = buildClientSecretPayload();
        } else {
            data = buildJWTPayload();
        }

        return new UrlEncodedContent(data);
    }

    /**
     * build JWT assertion to authenticate client based on config
     *
     * @param clientId both subject + issuer of token
     * @param audience for the token (the endpoint/service being called)
     * @return JWT assertion as a string
     */
    @SneakyThrows
    protected String buildJwtAssertion(String clientId, String audience) {
        //https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-certificate-credentials
        JsonWebSignature.Header header = new JsonWebSignature.Header();
        header.setType("JWT");
        header.setAlgorithm("RS256");

        setJWTCustomHeaders(header);

        JsonWebToken.Payload payload = buildPayload(clientId, audience);

        return JsonWebSignature.signUsingRsaSha256(
                getServiceAccountPrivateKey(), jsonFactory, header, payload);
    }

    protected void setJWTCustomHeaders(JsonWebSignature.Header header) {
        header.setX509Thumbprint(encodeKeyId(config.getConfigPropertyOrError(ConfigProperty.PRIVATE_KEY_ID)));
    }

    private Map<String, String> buildClientSecretPayload() {
        Map<String, String> data = new HashMap<>();

        data.put("grant_type", getGrantType());
        data.put("client_id", config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID));
        data.put("client_secret", config.getConfigPropertyOrError(ConfigProperty.CLIENT_SECRET));

        return data;
    }

    private Map<String, String> buildJWTPayload() {
        //implementation of:
        // https://docs.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-client-creds-grant-flow#get-a-token

        String oauthClientId = config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID);
        String tokenEndpoint =
                config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT);

        Map<String, String> data = new TreeMap<>();
        //https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2
        data.put(PARAM_GRANT_TYPE, "client_credentials");
        config.getConfigPropertyAsOptional(ConfigProperty.TOKEN_SCOPE)
                .ifPresent(r -> data.put(PARAM_SCOPE, r)); // 'scope' param is optional, per RFC

        //https://datatracker.ietf.org/doc/html/rfc7521#section-4.2
        data.put(PARAM_CLIENT_ASSERTION_TYPE, CLIENT_ASSERTION_TYPE_JWT);
        data.put(PARAM_CLIENT_ASSERTION, buildJwtAssertion(oauthClientId, tokenEndpoint));
        data.put(PARAM_CLIENT_ID, oauthClientId);

        return data;
    }

    private JsonWebToken.Payload buildPayload(String clientId, String audience) {
        JsonWebToken.Payload payload = new JsonWebToken.Payload();
        Instant currentTime = clock.instant();

        payload.setAudience(audience);

        payload.setIssuer(clientId);
        payload.setSubject(clientId);

        payload.setJwtId(uuidGenerator.get().toString()); // MSFT docs say a random GUID, which is what they call UUID

        payload.setIssuedAtTimeSeconds(currentTime.getEpochSecond());
        payload.setExpirationTimeSeconds(currentTime.plus(DEFAULT_EXPIRATION_DURATION).getEpochSecond());
        return payload;
    }

    //implements encoding X.509 certificate hash (also known as the cert's SHA-1 thumbprint) as a
    // Base64url string value, which is how MSFT wants it in their JWT assertions
    @SneakyThrows
    @VisibleForTesting
    String encodeKeyId(String hexKey) {
        //TODO: replace with java.util.HexFormat.of().parseHex(s) ... but that's only from Java 17
        byte[] fromHex = Hex.decodeHex(hexKey);
        return Base64.getUrlEncoder().encodeToString(fromHex);
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

    private CredentialFlowType getCredentialsType() {
        AtomicReference<CredentialFlowType> result = new AtomicReference<>(CredentialFlowType.JWT_ASSERTION);

        config.getConfigPropertyAsOptional(ConfigProperty.CREDENTIALS_FLOW)
                .ifPresent(i -> {
                    if (i.equalsIgnoreCase("client_secret")) {
                        result.set(CredentialFlowType.CLIENT_SECRET);
                    } else {
                        result.set(CredentialFlowType.JWT_ASSERTION);
                    }
                });

        return result.get();
    }
}