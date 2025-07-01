package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import co.worklytics.psoxy.gateway.SecretStore;
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
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;

/**
 * implementation of an Access Token Request (per RFC6749 4.4.2) authenticated by an assertion (RFC 7521)
 * based on private key id + private key secret for building the JWT assertion to use
 * <p>
 * see
 * - <a href="https://datatracker.ietf.org/doc/html/rfc7521">...</a>
 * - <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2">...</a>
 */
@Log
@NoArgsConstructor(onConstructor_ = @Inject)
public class ClientCredentialsGrantTokenRequestBuilder
        implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder, RequiresConfiguration {

    public static final String GRANT_TYPE = "client_credentials";

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
        JWT_ASSERTION,
        ;

        static Optional<CredentialFlowType> parse(String configValue) {
            if (configValue == null || configValue.isEmpty()) {
                return Optional.empty();
            }
            return switch (configValue.toLowerCase(Locale.ROOT)) {
                case "client_secret" -> Optional.of(CLIENT_SECRET);
                case "jwt_assertion" -> Optional.of(JWT_ASSERTION);
                default ->
                    throw new IllegalArgumentException("Unknown credential flow type configured: " + configValue);
            };
        }
    }

    // 'client_credentials' is MSFT
    //for Google, this is "urn:ietf:params:oauth:grant-type:jwt-bearer"
    @Getter(onMethod_ = @Override)
    private final String grantType = GRANT_TYPE;

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
    SecretStore secretStore;
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

        return JsonWebSignature.signUsingRsaSha256(getPrivateKey(), jsonFactory, header, payload);
    }

    @VisibleForTesting
    void setJWTCustomHeaders(JsonWebSignature.Header header) {
        String configuredValue = secretStore.getConfigPropertyOrError(ConfigProperty.PRIVATE_KEY_ID);

        // these private key ids are fingerprints generated by openssl tooling. we've seen on
        // some systems that fingerprints end up with extra garbage.
        configuredValue = StringUtils.trimToEmpty(configuredValue);

        // some versions of openssl prefix the fingerprint with "sha1 Fingerprint=" which we don't trim properly
        configuredValue = StringUtils.removeStart(configuredValue, "sha1 Fingerprint=");

        //openssl formats with : between each hex-encoded byte; our helper scripts cleanup, but just in case:
        configuredValue = StringUtils.replaceChars(configuredValue, ":", "");

        header.setX509Thumbprint(encodeKeyId(configuredValue));
    }

    private Map<String, String> buildClientSecretPayload() {
        Map<String, String> data = new HashMap<>();

        data.put("grant_type", getGrantType());
        data.put("client_id", getClientId());
        data.put("client_secret",
            secretStore.getConfigPropertyOrError(ConfigProperty.CLIENT_SECRET));

        return data;
    }

    private Map<String, String> buildJWTPayload() {
        //implementation of:
        // https://docs.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-client-creds-grant-flow#get-a-token

        String oauthClientId = getClientId();
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
        byte[] fromHex =  HexFormat.of().parseHex(hexKey);
        return Base64.getUrlEncoder().encodeToString(fromHex);
    }

    /**
     * get private key to be used to sign the JWT assertion
     * @return the private key
     * @throws IOException if cannot read/parse
     * @throws NoSuchElementException if no private key is configured in the secret store
     */
    private PrivateKey getPrivateKey() throws IOException {

        ConfigService.ConfigValueWithMetadata value = secretStore.getConfigPropertyWithMetadata(ConfigProperty.PRIVATE_KEY)
            .orElseThrow(() -> new NoSuchElementException("No PRIVATE_KEY found in secret store"));

        value.getLastModifiedDate().ifPresent(lastModified -> {
            if (lastModified.isBefore(clock.instant().minus(180, ChronoUnit.DAYS))) {
                log.log(Level.WARNING, "Private key last modified in secret store more than 180 days ago, may be expired");
            }
        });

        // TODO: flexibility to base64-encode the private key, or not


        Reader reader = new StringReader(value.getValue());
        PemReader.Section section = PemReader.readFirstSectionAndClose(reader, "PRIVATE KEY");
        if (section == null) {
            throw new IOException("Invalid PKCS8 data.");
        }
        byte[] bytes = section.getBase64DecodedBytes();
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
        try {
            KeyFactory keyFactory = SecurityUtils.getRsaKeyFactory();
            return keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Private key uses unrecognized/unsupported encryption algorithm", e);
        } catch (InvalidKeySpecException e) {
            throw new IOException("Private key spec couldn't be read", e);
        }
    }

    private CredentialFlowType getCredentialsType() {
        return config.getConfigPropertyAsOptional(ConfigProperty.CREDENTIALS_FLOW)
            .map(v -> CredentialFlowType.parse(v).orElse(CredentialFlowType.JWT_ASSERTION))
            .orElse(CredentialFlowType.JWT_ASSERTION);
    }

    private String getClientId() {
        return config.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID)
            .orElseGet(() -> secretStore.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID));
    }
}
