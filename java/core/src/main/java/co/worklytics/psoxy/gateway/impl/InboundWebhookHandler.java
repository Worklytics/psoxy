package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.auth.PublicKeyRef;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import co.worklytics.psoxy.gateway.output.Output;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import dagger.Lazy;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;

import javax.inject.Inject;
import javax.inject.Named;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * general handler for inbound webhooks (Webhook Collector mode of proxy)
 * to be wrapped by host-platform specific handlers (e.g. GCP, AWS, etc.), which adapt the
 * incoming webhook requests to the HttpEventRequest interface,
 * and resulting HttpEventResponse to the host platform's response format.
 *
 */
@Log
public class InboundWebhookHandler {

    static final Duration MAX_TOKEN_AGE = Duration.ofHours(1);

    private final Output output;
    private final WebhookSanitizer webhookSanitizer;
    private final ConfigService configService;
    private final Set<PublicKeyStoreClient> publicKeyStoreClients;
    private final Clock clock;

    @Inject
    public InboundWebhookHandler(Lazy<WebhookSanitizer> webhookSanitizerProvider,
                                 @Named("forWebhooks") Output output,
                                 ConfigService configService,
                                 Set<PublicKeyStoreClient> publicKeyStoreClients,
                                 Clock clock) {
        this.webhookSanitizer = webhookSanitizerProvider.get(); // avoids trying to instantiate WebhookSanitizerImpl when we don't need one
        this.output = output;
        this.configService = configService;
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

        Optional<SignedJWT> authToken;

        if (authorizationHeader.isEmpty()) {
            boolean authHeaderRequired = configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.REQUIRE_AUTHORIZATION_HEADER).map(Boolean::parseBoolean).orElse(true);
            // validate that configuration doesn't require verification of Authorization headers
            if (authHeaderRequired) {
                return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_UNAUTHORIZED)
                    .body("Authorization header is required, but not present in request")
                    .build();
            }
        } else {
            try {
                authToken = Optional.of(SignedJWT.parse(authorizationHeader.get()));
            } catch (ParseException e) {
                return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_BAD_REQUEST)
                    .body("Authorization header is present, but could not be parsed as a JWT: " + e.getMessage())
                    .build();
            }

            // verify authorization header is signed with one of the acceptable public keys
            Optional<String> validationError = this.validate(authToken.get(), acceptableAuthKeys());

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



        ProcessedContent sanitized = webhookSanitizer.sanitize(request);

        output.write(sanitized);

        return HttpEventResponse.builder()
            .statusCode(HttpStatus.SC_OK)
            .build();
    }

    /**
     * returns a failure message if invalid, or empty otherwise
     * @param signedJWT
     * @param publicKeys
     * @return optional with the failure, if any
     */
    @SneakyThrows
    public Optional<String> validate(SignedJWT signedJWT, Collection<RSAPublicKey> publicKeys) {

        boolean signatureValid = publicKeys.stream()
            .filter(k -> {
                JWSVerifier verifier = new RSASSAVerifier(k);
                try {
                    return signedJWT.verify(verifier);
                } catch (JOSEException e) {
                    throw new RuntimeException(e);
                }
            })
            .findFirst().isPresent();

        if (!signatureValid ) {
            return Optional.of("JWT verification failed due to invalid signature");
        }
        if (signedJWT.getJWTClaimsSet() == null) {
            return Optional.of("Auth token invalid because JWT claims set is null");
        }

        if (signedJWT.getJWTClaimsSet().getIssueTime() == null) {
            return Optional.of("Auth token invalid because issued at time (iat) is null");
        }

        if (signedJWT.getJWTClaimsSet().getExpirationTime() == null) {
            return Optional.of("Auth token invalid because expiration time (exp) is null");
        }

        Instant now = clock.instant();
        if (signedJWT.getJWTClaimsSet().getExpirationTime().before(Date.from(now))) {
            return Optional.of("Auth token invalid because its expiration time (exp) is in the past: " + signedJWT.getJWTClaimsSet().getExpirationTime());
        }

        // TODO: validate issuer?? do we care?

        return Optional.empty();
    }

    Collection<RSAPublicKey> acceptableAuthKeys() {
        return Arrays.stream(configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.ACCEPTED_AUTH_KEYS).orElse("").split(","))
            .map(String::trim)
            .filter(keyRef -> !keyRef.isEmpty())
            .map(PublicKeyRef::fromString)
            .flatMap(publicKeyRef -> {
                Optional<PublicKeyStoreClient> client = publicKeyStoreClients.stream().filter(c -> c.getId().equals(publicKeyRef.store())).findAny();
                if (client.isEmpty()) {
                    throw new IllegalArgumentException("No public key store client found for: " + publicKeyRef.store());
                }
                return client.get().getPublicKeys(publicKeyRef).stream();
            }).collect(Collectors.toList());
    }
}
