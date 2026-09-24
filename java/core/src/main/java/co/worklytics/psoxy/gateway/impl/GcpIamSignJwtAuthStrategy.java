package co.worklytics.psoxy.gateway.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ContentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.UrlEncodedContent;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.oauth.AuthUtils;
import co.worklytics.psoxy.gateway.impl.oauth.CanonicalOAuthAccessTokenResponseDto;
import com.nimbusds.jwt.JWTClaimNames;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

/**
 * Domain-wide delegation without a downloaded service-account key.
 *
 * <p>Workspace DWD requires a JWT with {@code iss} = the DWD-enabled service account and
 * {@code sub} = the Workspace user. {@code ServiceAccountCredentials.createDelegated(user)} does
 * that by signing locally with a JSON key. Metadata / WIF credentials implement
 * {@code createDelegated} as a no-op, so this strategy instead:
 * <ol>
 *   <li>authenticates the process as a GCP principal (explicit {@link GoogleCloudProcessIdentity},
 *       never ADC)</li>
 *   <li>calls IAM Credentials {@code signJwt} so Google signs that JWT with the DWD SA's
 *       Google-managed key</li>
 *   <li>exchanges the signed JWT at {@value #TOKEN_SERVER_URL}
 *       ({@link AuthUtils#JWT_BEARER_GRANT_TYPE})</li>
 * </ol>
 *
 * <p>Same strategy on GCP and AWS; process identity is selected by
 * {@link ConfigProperty#PROCESS_IDENTITY_SOURCE}:
 * <ul>
 *   <li>{@code gcp_hosted} — Cloud Function / Cloud Run / GCE attached SA via metadata</li>
 *   <li>{@code aws_wif} — AWS IAM role → GCP WIF, then {@code signJwt}</li>
 * </ul>
 *
 * @see GoogleCloudPlatformServiceAccountKeyAuthStrategy key-based DWD (createDelegated)
 * @see <a href="https://knowledge.workspace.google.com/admin/apps/domain-wide-delegation-best-practices">Workspace DWD best practices</a>
 */
@Log
@NoArgsConstructor(onConstructor_ = @Inject)
public class GcpIamSignJwtAuthStrategy implements SourceAuthStrategy {

    public static final String CONFIG_IDENTIFIER = "gcp_iam_sign_jwt";

    static final String TOKEN_SERVER_URL = "https://oauth2.googleapis.com/token";
    static final String IAM_CREDENTIALS_HOST = "iamcredentials.googleapis.com";
    static final String SIGN_JWT_METHOD = "signJwt";
    static final String IAM_SIGN_JWT_URL_FORMAT =
        "https://" + IAM_CREDENTIALS_HOST + "/v1/projects/-/serviceAccounts/%s:" + SIGN_JWT_METHOD;

    /**
     * JWT claim names for the DWD bearer token. Registered names from {@link JWTClaimNames};
     * {@link #SCOPE} is the OAuth claim Google expects (not RFC 7519).
     */
    static final class Claims {
        static final String ISSUER = JWTClaimNames.ISSUER;
        static final String SUBJECT = JWTClaimNames.SUBJECT;
        static final String AUDIENCE = JWTClaimNames.AUDIENCE;
        static final String ISSUED_AT = JWTClaimNames.ISSUED_AT;
        static final String EXPIRATION_TIME = JWTClaimNames.EXPIRATION_TIME;
        static final String SCOPE = "scope";

        private Claims() {}
    }

    static final Duration JWT_LIFETIME = Duration.ofHours(1);
    static final Duration DEFAULT_ACCESS_TOKEN_EXPIRATION = Duration.ofHours(1);

    /**
     * google-auth-library type thrown on JWT/token-exchange failure; package-private, so we
     * match by class name rather than {@code instanceof}.
     */
    private static final String GOOGLE_AUTH_EXCEPTION_CLASS = "com.google.auth.oauth2.GoogleAuthException";

    @Getter
    private final String configIdentifier = CONFIG_IDENTIFIER;

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        /**
         * DWD-enabled GCP service account; JWT {@code iss}, and the account IAM {@code signJwt}
         * is invoked on.
         */
        SERVICE_ACCOUNT_EMAIL,
        /**
         * Workspace OAuth scopes granted via domain-wide delegation; space-delimited; become the
         * JWT {@code scope} claim.
         */
        OAUTH_SCOPES,
        /**
         * Selects {@link GoogleCloudProcessIdentity}; {@code gcp_hosted} or {@code aws_wif}.
         */
        PROCESS_IDENTITY_SOURCE,
        ;

        @Getter(onMethod_ = @Override)
        private final SupportedSource supportedSource = SupportedSource.ENV_VAR;
    }

    @Inject ConfigService config;
    @Inject HttpTransportFactory httpTransportFactory;
    @Inject ObjectMapper objectMapper;
    /**
     * All {@link GoogleCloudProcessIdentity} impls registered via Dagger {@code @IntoSet}; one is
     * selected by {@link ConfigProperty#PROCESS_IDENTITY_SOURCE} (same pattern as
     * {@link SourceAuthStrategy} / OAuth grant-type builders).
     */
    @Inject Set<GoogleCloudProcessIdentity> processIdentities;
    @Inject Clock clock;

    /**
     * parsed from {@link ConfigProperty#OAUTH_SCOPES}; kept to avoid repeated split.
     */
    Set<String> scopes;

    /**
     * Cached credentials for this process as a GCP principal (Cloud Function attached SA, or AWS
     * WIF). Used only to call IAM {@code signJwt} — not the Workspace-user token returned by
     * {@link #getCredentials}. {@code volatile} + synchronized lazy init for concurrent requests.
     */
    transient volatile GoogleCredentials processCredentials;

    transient LoadingCache<String, Credentials> credentialsCache = CacheBuilder.newBuilder()
        .concurrencyLevel(1)
        // Worklytics transfer pipelines shard per Google Workspace user; one cached credential per active shard
        .maximumSize(50)
        .recordStats()
        .build(CacheLoader.from(this::buildCredentials));

    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {
        return credentialsCache.getUnchecked(userToImpersonate.orElse(""));
    }

    @Override
    public boolean isSourceAuthFailure(IOException e) {
        Throwable cause = e;
        while (cause != null) {
            if (GOOGLE_AUTH_EXCEPTION_CLASS.equals(cause.getClass().getName())) {
                return true;
            }
            if (cause instanceof HttpResponseException) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("access token")
                    || lower.contains(TOKEN_SERVER_URL)
                    || lower.contains(IAM_CREDENTIALS_HOST)
                    || lower.contains(SIGN_JWT_METHOD.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        Stream<ConfigService.ConfigProperty> properties = Stream.of(
            ConfigProperty.SERVICE_ACCOUNT_EMAIL,
            ConfigProperty.OAUTH_SCOPES,
            ConfigProperty.PROCESS_IDENTITY_SOURCE
        );
        try {
            properties = Stream.concat(properties,
                getProcessIdentity().getRequiredConfigProperties().stream());
        } catch (RuntimeException ignored) {
            // PROCESS_IDENTITY_SOURCE missing or unknown; health check reports the base props
        }
        return properties.collect(Collectors.toSet());
    }

    @Override
    public List<String> validateConfigValues() {
        try {
            getProcessIdentity();
            return Collections.emptyList();
        } catch (RuntimeException e) {
            return Collections.singletonList(e.getMessage());
        }
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        Stream<ConfigService.ConfigProperty> properties = Stream.of(ConfigProperty.values());
        if (processIdentities != null) {
            properties = Stream.concat(properties,
                processIdentities.stream()
                    .flatMap(identity -> identity.getAllConfigProperties().stream()));
        }
        return properties.collect(Collectors.toSet());
    }

    Credentials buildCredentials(@NonNull String accountToImpersonate) {
        String user = StringUtils.trimToNull(accountToImpersonate);
        return OAuth2CredentialsWithRefresh.newBuilder()
            .setRefreshHandler(() -> refreshDelegatedAccessToken(user))
            .build();
    }

    @VisibleForTesting
    AccessToken refreshDelegatedAccessToken(String userToImpersonate) throws IOException {
        String serviceAccountEmail =
            config.getConfigPropertyOrError(ConfigProperty.SERVICE_ACCOUNT_EMAIL);
        String payload = buildJwtPayload(serviceAccountEmail, userToImpersonate);
        String signedJwt = signJwt(serviceAccountEmail, payload);
        return exchangeJwtForAccessToken(signedJwt);
    }

    @VisibleForTesting
    String buildJwtPayload(String serviceAccountEmail, String userToImpersonate) {
        long iat = clock.instant().getEpochSecond();
        long exp = iat + JWT_LIFETIME.toSeconds();

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(Claims.ISSUER, serviceAccountEmail);
        claims.put(Claims.AUDIENCE, TOKEN_SERVER_URL);
        claims.put(Claims.ISSUED_AT, iat);
        claims.put(Claims.EXPIRATION_TIME, exp);
        claims.put(Claims.SCOPE, String.join(" ", getScopes()));
        if (StringUtils.isNotBlank(userToImpersonate)) {
            claims.put(Claims.SUBJECT, userToImpersonate);
        }
        return writeJson(claims);
    }

    String signJwt(String serviceAccountEmail, String jwtPayload) throws IOException {
        Map<String, String> body = Map.of("payload", jwtPayload);
        ByteArrayContent content = new ByteArrayContent(ContentType.APPLICATION_JSON.getMimeType(),
            writeJson(body).getBytes(StandardCharsets.UTF_8));

        HttpTransport transport = httpTransportFactory.create();
        HttpRequestFactory requestFactory = transport.createRequestFactory(
            new HttpCredentialsAdapter(getProcessCredentials()));
        GenericUrl url = new GenericUrl(String.format(IAM_SIGN_JWT_URL_FORMAT, serviceAccountEmail));
        HttpRequest request = requestFactory.buildPostRequest(url, content);

        HttpResponse response = request.execute();
        try {
            JsonNode node = objectMapper.readTree(response.getContent());
            JsonNode signedJwt = node.get("signedJwt");
            if (signedJwt == null || signedJwt.isNull() || StringUtils.isBlank(signedJwt.asText())) {
                throw new IOException("IAM signJwt response missing signedJwt");
            }
            return signedJwt.asText();
        } finally {
            response.disconnect();
        }
    }

    AccessToken exchangeJwtForAccessToken(String signedJwt) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put(AuthUtils.PARAM_GRANT_TYPE, AuthUtils.JWT_BEARER_GRANT_TYPE);
        form.put(AuthUtils.PARAM_ASSERTION, signedJwt);

        HttpTransport transport = httpTransportFactory.create();
        HttpRequest request = transport.createRequestFactory()
            .buildPostRequest(new GenericUrl(TOKEN_SERVER_URL), new UrlEncodedContent(form));

        HttpResponse response = request.execute();
        try {
            CanonicalOAuthAccessTokenResponseDto tokenResponse = objectMapper
                .readerFor(CanonicalOAuthAccessTokenResponseDto.class)
                .readValue(response.getContent());
            if (StringUtils.isBlank(tokenResponse.getAccessToken())) {
                throw new IOException("Token exchange response missing access_token");
            }
            int expiresIn = Optional.ofNullable(tokenResponse.getExpiresIn())
                .orElse((int) DEFAULT_ACCESS_TOKEN_EXPIRATION.toSeconds());
            return new AccessToken(tokenResponse.getAccessToken(),
                Date.from(clock.instant().plusSeconds(expiresIn)));
        } finally {
            response.disconnect();
        }
    }

    synchronized GoogleCredentials getProcessCredentials() {
        if (processCredentials == null) {
            GoogleCloudProcessIdentity identity = getProcessIdentity();
            log.info("Using Google Cloud process identity: " + identity.getConfigIdentifier());
            processCredentials = identity.getCredentials();
        }
        return processCredentials;
    }

    @VisibleForTesting
    GoogleCloudProcessIdentity getProcessIdentity() {
        String identifier = config.getConfigPropertyOrError(ConfigProperty.PROCESS_IDENTITY_SOURCE);
        return processIdentities.stream()
            .filter(identity -> Objects.equals(identifier, identity.getConfigIdentifier()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No GoogleCloudProcessIdentity matching PROCESS_IDENTITY_SOURCE: " + identifier
                    + ". Known: "
                    + processIdentities.stream()
                        .map(GoogleCloudProcessIdentity::getConfigIdentifier)
                        .collect(Collectors.joining(", "))));
    }

    private synchronized Set<String> getScopes() {
        if (scopes == null) {
            scopes = Arrays.stream(config.getConfigPropertyOrError(ConfigProperty.OAUTH_SCOPES).split(" "))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return scopes;
    }

    @SneakyThrows
    private String writeJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
