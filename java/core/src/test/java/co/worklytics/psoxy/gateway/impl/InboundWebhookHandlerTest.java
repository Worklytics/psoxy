package co.worklytics.psoxy.gateway.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.SignedJWT;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.WebhookCollectorModeConfig;
import co.worklytics.psoxy.gateway.impl.output.NoOutput;
import dagger.Lazy;

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
            WebhookCollectorModeConfig.builder().build(),
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


    @ParameterizedTest
    @ValueSource(strings = {
        "Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6InByb2plY3RzL3Bzb3h5LWRldi1lcmlrL2xvY2F0aW9ucy91cy1jZW50cmFsMS9rZXlSaW5ncy9wc294eS1kZXYtZXJpay0vY3J5cHRvS2V5cy9wc294eS1kZXYtZXJpay1sbG0tcG9ydGFsLXdlYmhvb2stYXV0aC1rZXkiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL3Bzb3h5LWRldi1lcmlrLWxsbS1wb3J0YWwtYm92djNmcjI2cS11Yy5hLnJ1bi5hcHAiLCJzdWIiOiJlcmlrQHdvcmtseXRpY3MuY28iLCJhdWQiOiJodHRwczovL3Bzb3h5LWRldi1lcmlrLWxsbS1wb3J0YWwtYm92djNmcjI2cS11Yy5hLnJ1bi5hcHAiLCJpYXQiOjE3NTM3MjM4MDgsImV4cCI6MTc1MzcyNzQwOH0.Yq4_7OBOxs-WprsncVq_CFn_5fnVffQYdwNEqyM5GRDhbHKnCQNPDeW9Ilunw39xRUM0tYRFyd6ktnwuCkuSLaqlO_UkA5fLkVKAEOpYF9o3_inobhFSFV4JSjYNvspUSdnBJ9TMhcLX0I8MgX6p3xn61TRFFedUy4BAaAqjJd5FQQO0Udt8CQomr3sbld5Z2iY0zO-UzjmXChD7UCA1QVm_S0mPxGj0X25C9Nqj8nWx-mV2Vy0i2qGA-hNDPwFnMxjTDkHu_EPSLmTgbqeFi7uu_HJAOcFXEbz3ReyeobBA3OGxP4igl_4qDKwPmmmPAw4fPmwFGL42VjjbE6-gZw",
        "eyJhbGciOiJSUzI1NiIsImtpZCI6InByb2plY3RzL3Bzb3h5LWRldi1lcmlrL2xvY2F0aW9ucy91cy1jZW50cmFsMS9rZXlSaW5ncy9wc294eS1kZXYtZXJpay0vY3J5cHRvS2V5cy9wc294eS1kZXYtZXJpay1sbG0tcG9ydGFsLXdlYmhvb2stYXV0aC1rZXkiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL3Bzb3h5LWRldi1lcmlrLWxsbS1wb3J0YWwtYm92djNmcjI2cS11Yy5hLnJ1bi5hcHAiLCJzdWIiOiJlcmlrQHdvcmtseXRpY3MuY28iLCJhdWQiOiJodHRwczovL3Bzb3h5LWRldi1lcmlrLWxsbS1wb3J0YWwtYm92djNmcjI2cS11Yy5hLnJ1bi5hcHAiLCJpYXQiOjE3NTM3MjM4MDgsImV4cCI6MTc1MzcyNzQwOH0.Yq4_7OBOxs-WprsncVq_CFn_5fnVffQYdwNEqyM5GRDhbHKnCQNPDeW9Ilunw39xRUM0tYRFyd6ktnwuCkuSLaqlO_UkA5fLkVKAEOpYF9o3_inobhFSFV4JSjYNvspUSdnBJ9TMhcLX0I8MgX6p3xn61TRFFedUy4BAaAqjJd5FQQO0Udt8CQomr3sbld5Z2iY0zO-UzjmXChD7UCA1QVm_S0mPxGj0X25C9Nqj8nWx-mV2Vy0i2qGA-hNDPwFnMxjTDkHu_EPSLmTgbqeFi7uu_HJAOcFXEbz3ReyeobBA3OGxP4igl_4qDKwPmmmPAw4fPmwFGL42VjjbE6-gZw"
    })
    void testParseJwt(String authorizationHeader) throws ParseException {
        SignedJWT jwt = handler.parseJwt(authorizationHeader);
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
