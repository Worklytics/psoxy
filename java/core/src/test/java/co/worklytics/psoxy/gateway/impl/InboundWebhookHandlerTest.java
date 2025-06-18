package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import co.worklytics.psoxy.gateway.impl.output.NoOutput;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.SignedJWT;
import dagger.Lazy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.inject.Named;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class InboundWebhookHandlerTest {
    private InboundWebhookHandler handler;
    private Clock fixedClock;
    private static final Instant FIXED_NOW = Instant.parse("2025-06-18T12:00:00Z");

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        handler = new InboundWebhookHandler(
            mock(Lazy.class),
            new NoOutput(),
            mock(ConfigService.class),
            Collections.emptySet(),
            fixedClock);
    }

    static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    static SignedJWT createSignedJwt(Map<String, Object> claims, RSAPrivateKey privateKey) throws Exception {
        JWSSigner signer = new RSASSASigner(privateKey);
        JWSHeader header = new JWSHeader(JWSAlgorithm.RS256);
        com.nimbusds.jwt.JWTClaimsSet.Builder builder = new com.nimbusds.jwt.JWTClaimsSet.Builder();
        if (claims.containsKey("iat")) builder.issueTime((Date) claims.get("iat"));
        if (claims.containsKey("exp")) builder.expirationTime((Date) claims.get("exp"));
        com.nimbusds.jwt.JWTClaimsSet claimSet = builder.build();
        SignedJWT jwt = new SignedJWT(header, claimSet);
        jwt.sign(signer);
        return jwt;
    }

    static Stream<TestCase> provideJwtValidationCases() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        Instant now = FIXED_NOW;
        Duration skew = Duration.ofMinutes(5);
        Duration maxFuture = Duration.ofHours(1);

        // Valid JWT
        Map<String, Object> validClaims = new HashMap<>();
        validClaims.put("iat", Date.from(now.minus(Duration.ofMinutes(1))));
        validClaims.put("exp", Date.from(now.plus(Duration.ofMinutes(10))));
        SignedJWT validJwt = createSignedJwt(validClaims, privateKey);

        // Expired JWT
        Map<String, Object> expiredClaims = new HashMap<>();
        expiredClaims.put("iat", Date.from(now.minus(Duration.ofHours(2))));
        expiredClaims.put("exp", Date.from(now.minus(Duration.ofMinutes(10))));
        SignedJWT expiredJwt = createSignedJwt(expiredClaims, privateKey);

        // JWT with future iat
        Map<String, Object> futureIatClaims = new HashMap<>();
        futureIatClaims.put("iat", Date.from(now.plus(Duration.ofMinutes(10))));
        futureIatClaims.put("exp", Date.from(now.plus(Duration.ofMinutes(20))));
        SignedJWT futureIatJwt = createSignedJwt(futureIatClaims, privateKey);

        // JWT with exp too far in future
        Map<String, Object> expTooFarClaims = new HashMap<>();
        expTooFarClaims.put("iat", Date.from(now));
        expTooFarClaims.put("exp", Date.from(now.plus(Duration.ofDays(366))));
        SignedJWT expTooFarJwt = createSignedJwt(expTooFarClaims, privateKey);

        // JWT with null iat
        Map<String, Object> nullIatClaims = new HashMap<>();
        nullIatClaims.put("exp", Date.from(now.plus(Duration.ofMinutes(10))));
        SignedJWT nullIatJwt = createSignedJwt(nullIatClaims, privateKey);

        // JWT with null exp
        Map<String, Object> nullExpClaims = new HashMap<>();
        nullExpClaims.put("iat", Date.from(now.minus(Duration.ofMinutes(1))));
        SignedJWT nullExpJwt = createSignedJwt(nullExpClaims, privateKey);

        return Stream.of(
            new TestCase(validJwt, Collections.singleton(publicKey), Optional.empty(), "Valid JWT should pass validation"),
            new TestCase(expiredJwt, Collections.singleton(publicKey), Optional.of("Auth token invalid because its expiration time (exp) is too far in the past"), "Expired JWT should fail validation"),
            new TestCase(futureIatJwt, Collections.singleton(publicKey), Optional.of("Auth token invalid because its issued at time (iat) is in the future"), "JWT with future iat should fail validation"),
            new TestCase(expTooFarJwt, Collections.singleton(publicKey), Optional.of("Auth token invalid because its expires too far in future"), "JWT with exp too far in future should fail validation"),
            new TestCase(nullIatJwt, Collections.singleton(publicKey), Optional.of("Auth token invalid because issued at time (iat) is null"), "JWT with null iat should fail validation"),
            new TestCase(nullExpJwt, Collections.singleton(publicKey), Optional.of("Auth token invalid because expiration time (exp) is null"), "JWT with null exp should fail validation")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideJwtValidationCases")
    void testValidate(TestCase testCase) {
        Optional<String> result = handler.validate(testCase.jwt, testCase.publicKeys);
        if (testCase.expectedError.isPresent()) {
            assertTrue(result.isPresent(), "Expected error but got none");
            assertTrue(result.get().startsWith(testCase.expectedError.get()), "Error message should start with expected");
        } else {
            assertFalse(result.isPresent(), "Expected no error but got: " + result.orElse(""));
        }
    }

    static class TestCase {
        final SignedJWT jwt;
        final Collection<RSAPublicKey> publicKeys;
        final Optional<String> expectedError;
        final String displayName;
        TestCase(SignedJWT jwt, Collection<RSAPublicKey> publicKeys, Optional<String> expectedError, String displayName) {
            this.jwt = jwt;
            this.publicKeys = publicKeys;
            this.expectedError = expectedError;
            this.displayName = displayName;
        }
        @Override public String toString() { return displayName; }
    }
}
