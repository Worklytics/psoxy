package co.worklytics.psoxy.gateway.impl;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.WebhookCollectorModeConfig;
import co.worklytics.psoxy.gateway.auth.JwtAuthorizedResource;
import co.worklytics.psoxy.gateway.auth.PublicKeyRef;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient.PublicKeyVersionId;
import co.worklytics.psoxy.gateway.output.Output;
import dagger.Lazy;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

/**
 * general handler for inbound webhooks (Webhook Collector mode of proxy)
 * to be wrapped by host-platform specific handlers (e.g. GCP, AWS, etc.), which adapt the
 * incoming webhook requests to the HttpEventRequest interface,
 * and resulting HttpEventResponse to the host platform's response format.
 *
 */
@Log
public class InboundWebhookHandler implements JwtAuthorizedResource {

    // main point of this is to ensure that servers haven't issued super-long-lived tokens by mistake
    // for example, by setting `exp` in milliseconds since epoch, instead of seconds since epoch
    static final Duration MAX_FUTURE_TOKEN_EXPIRATION = Duration.ofDays(365); // 1 year

    // check and allow *some* clock skew in iat and exp
    static final Duration ALLOWED_CLOCK_SKEW = Duration.ofMinutes(2);

    private final Output output;
    private final WebhookSanitizer webhookSanitizer;
    private final ConfigService configService;
    private final WebhookCollectorModeConfig webhookCollectorModeConfig;
    private final Set<PublicKeyStoreClient> publicKeyStoreClients;
    private final Clock clock;

    @Inject
    public InboundWebhookHandler(Lazy<WebhookSanitizer> webhookSanitizerProvider,
                                 @Named("forWebhooks") Output output,
                                 ConfigService configService,
                                 WebhookCollectorModeConfig webhookCollectorModeConfig,
                                 Set<PublicKeyStoreClient> publicKeyStoreClients,
                                 Clock clock) {
        this.webhookSanitizer = webhookSanitizerProvider.get(); // avoids trying to instantiate WebhookSanitizerImpl when we don't need one
        this.output = output;
        this.configService = configService;
        this.webhookCollectorModeConfig = webhookCollectorModeConfig;
        this.publicKeyStoreClients = publicKeyStoreClients;
        this.clock = clock;
    }

    /**
     * gets value of 'Authorization' header from request, if present.
     *
     * as canonical/standard 'Authorization' header may be intended for API gateway or some other intermediate layer,
     * prefer our bespoke 'X-Psoxy-Authorization' header, if present.
     *
     *
     * @param request to parse headers from
     * @return Optional containing the value of the 'Authorization' header, if present; otherwise, an empty Optional.
     */
    Optional<String> getAuthorizationHeader(HttpEventRequest request) {
        Optional<String> bespokeHeader = request.getHeader(ControlHeader.AUTHORIZATION.getHttpHeader());
        if (bespokeHeader.isPresent()) {
            return bespokeHeader;
        } else {
            // fall back to standard Authorization header
            return request.getHeader("Authorization");
        }
    }


    @SneakyThrows
    public HttpEventResponse handle(HttpEventRequest request) {
        Optional<String> authorizationHeader = getAuthorizationHeader(request);

        boolean isDevelopmentMode = configService.getConfigPropertyAsOptional(ProxyConfigProperty.IS_DEVELOPMENT_MODE).map(Boolean::parseBoolean).orElse(false);
        if (isDevelopmentMode) {
            log.info("Development mode enabled; auth header: " + authorizationHeader.orElse("not present"));
            log.info("Request: "  + request.prettyPrint());
        }

        if (request.getHttpMethod().equals("OPTIONS")) {
            // at least in AWS-cases, this is redundant; AWS API Gateway / Lambda Function URLs handle CORS preflight requests using configuration set in Terraform
            // see: https://docs.aws.amazon.com/lambda/latest/dg/urls-configuration.html#urls-cors

            // TODO: consider if we should cache this rather than build each time, as it will always be the same;
            // just need to do that in a thread-safe way

            // CORS preflight request
            return HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_NO_CONTENT)
                .header("Connection", "keep-alive") // correct??
                .header("Access-Control-Allow-Origin", webhookCollectorModeConfig.getAllowOrigins()) // q: configurable? what's the use-case to restrict this? if auth is based on Authorization header, no way for a malicious site to obtain and forge that, right?
                .header("Access-Control-Allow-Methods", "POST, OPTIONS") // TODO: make this configurable
                .header("Access-Control-Allow-Headers", "*")  // TODO: make this explicit?
                .build();
        }

        Optional<SignedJWT> authToken;

        if (authorizationHeader.isEmpty()) {
            boolean authHeaderRequired = webhookCollectorModeConfig.getRequireAuthorizationHeader();
            // validate that configuration doesn't require verification of Authorization headers
            if (authHeaderRequired) {
                return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_UNAUTHORIZED)
                    .body("Authorization header is required, but not present in request")
                    .build();
            }
        } else {
            try {
                SignedJWT jwt = this.parseJwt(authorizationHeader.get());
                authToken = Optional.of(jwt);
            } catch (ParseException e) {
                return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_BAD_REQUEST)
                    .body("Authorization header is present, but could not be parsed as a JWT: " + e.getMessage())
                    .build();
            }

            // verify authorization header is signed with one of the acceptable public keys
            Optional<String> validationError = this.validate(authToken.get(), acceptableAuthKeys().values());

            if (validationError.isPresent()) {
                return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_UNAUTHORIZED)
                    .body("Authorization header is present, but validation failed: " + validationError.get())
                    .build();
            }

            Map<String, Object> claims;
            try {
                claims = authToken.get().getJWTClaimsSet().getClaims();
            } catch (ParseException e) {
                claims = null;
            }

            if (!webhookSanitizer.verifyClaims(request, claims)) {
                return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_UNAUTHORIZED)
                    .body("Claims in Authorization header do not match request")
                    .build();
            }
        }

        // what if Authorization header sent, but not intended for Psoxy?
        // AWS API Gateway / Lambda AWS IAM authorizer will STRIPE the Authorization header, not pass it to the Lambda function - so OK
        // we'll get a plain 'Authorization' header ONLY in case where Auth=NONE on the Lambda Function URL or the API Gateway

        if (!webhookSanitizer.canAccept(request)) {
            return HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .build();
        }
        ProcessedContent sanitized;
        try {
            sanitized = webhookSanitizer.sanitize(request);
        } catch (Throwable e) {
            log.log(Level.WARNING, "Failed to sanitize incoming webhook request", e);
            return HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body("Failed to sanitize incoming webhook")
                .build();
        }

        try {
            output.write(sanitized);
            return HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_OK)
                .build();
        } catch (Output.WriteFailure e) {
            log.log(Level.WARNING, "Failed to write sanitized webhook payload to output", e);
            return HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .body("Failed to ingest incoming webhook")
                .build();
        }
    }

    static final String BEARER_PREFIX = "Bearer ";

    SignedJWT parseJwt(String authorizationHeader) throws java.text.ParseException {
        if (authorizationHeader.startsWith(BEARER_PREFIX)) {
            return SignedJWT.parse(authorizationHeader.substring(BEARER_PREFIX.length()));
        } else {
            log.warning("Authorization header does not start with Bearer prefix: " + authorizationHeader + "; will attempt to parse as JWT, but this is not standard OIDC");
            return SignedJWT.parse(authorizationHeader);
        }
    }

    /**
     * returns a failure message if invalid, or empty otherwise
     * @param jwt to validate
     * @return optional with the failure, if any
     */
    @Override
    public Optional<String> validate(SignedJWT jwt) {
        return validate(jwt, acceptableAuthKeys().values());
    }

    @Override
    public String getIssuer() {
        return webhookCollectorModeConfig.getAuthIssuer()
            .orElseThrow(() -> new IllegalStateException("AUTH_ISSUER is required but not configured"));
    }

    /**
     * @param signedJWT to validate
     * @param publicKeys to verify the signature against; if ANY of these keys can verify the signature, the JWT is considered valid
     * @return optional with the failure, if any
     */
    @SneakyThrows
    public Optional<String> validate(SignedJWT signedJWT, Collection<RSAPublicKey> publicKeys) {
        Instant now = clock.instant();

        if (signedJWT.getJWTClaimsSet() == null) {
            return Optional.of("Auth token invalid because JWT claims set is null");
        }

        //q: is this worth enforcing?
        if (signedJWT.getJWTClaimsSet().getIssueTime() == null) {
            return Optional.of("Auth token invalid because issued at time (iat) is null");
        }
        if (signedJWT.getJWTClaimsSet().getIssueTime().after(Date.from(now.plus(ALLOWED_CLOCK_SKEW)))) {
            return Optional.of("Auth token invalid because its issued at time (iat) is in the future: " + signedJWT.getJWTClaimsSet().getIssueTime());
        }

        if (signedJWT.getJWTClaimsSet().getExpirationTime() == null) {
            return Optional.of("Auth token invalid because expiration time (exp) is null");
        }

        if (signedJWT.getJWTClaimsSet().getExpirationTime().before(Date.from(now.minus(ALLOWED_CLOCK_SKEW)))) {
            return Optional.of("Auth token invalid because its expiration time (exp) is too far in the past: " + signedJWT.getJWTClaimsSet().getExpirationTime());
        }

        if (signedJWT.getJWTClaimsSet().getExpirationTime().after(Date.from(now.plus(MAX_FUTURE_TOKEN_EXPIRATION)))) {
            return Optional.of("Auth token invalid because its expires too far in future: " + signedJWT.getJWTClaimsSet().getExpirationTime());
        }

        boolean signatureValid = publicKeys.stream()
            .filter(k -> {
                JWSVerifier verifier = new RSASSAVerifier(k);
                try {
                    return signedJWT.verify(verifier);
                } catch (JOSEException e) {
                    log.log(Level.WARNING, "Failed to verify signature, will try other keys; " + e.getMessage(), e);
                    return false;
                }
            })
            .findFirst().isPresent();

        if (!signatureValid ) {
            return Optional.of("JWT verification failed due to invalid signature");
        }

        // TODO: validate issuer?? do we care? doesn't exactly help a lot, unless they give multiple tools access to use the same signing keys

        return Optional.empty();
    }

    public Map<PublicKeyVersionId, RSAPublicKey> acceptableAuthKeys() {
        return Arrays.stream(
                    webhookCollectorModeConfig.getAcceptedAuthKeys()
                        .orElse("")
                        .split(",")
                )
                .map(String::trim)
                .filter(keyRef -> !keyRef.isEmpty())
                .map(PublicKeyRef::fromString)
                .flatMap(publicKeyRef -> {
                    Optional<PublicKeyStoreClient> client = publicKeyStoreClients.stream()
                            .filter(c -> c.getId().equals(publicKeyRef.store()))
                            .findAny();
                    if (client.isEmpty()) {
                        throw new IllegalArgumentException("No public key store client found for: " + publicKeyRef.store());
                    }
                    // getPublicKeys returns Map<PublicKeyVersionId, RSAPublicKey>
                    Map<PublicKeyVersionId, RSAPublicKey> keys = client.get().getPublicKeys(publicKeyRef);

                    // Fix: iterate over entrySet, not keys.stream()
                    return keys.entrySet().stream();
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
