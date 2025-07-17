package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.SourceAuthModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import dagger.Component;
import lombok.SneakyThrows;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.security.Security;
import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CertificateGrantTokenRequestBuilderTest {

    final String clientId = "359050";
    final String tokenEndpoint = "https://api.github.com/app/installations/39494331/access_tokens";

    private final String EXAMPLE_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCoKLCwpGgaicC2\n" +
            "FAFvLL6J6GPkwdm7PkriVWfRrrSHzXDlFRcCbxy6HC/50fNV+2IHs/8EaudUfoNR\n" +
            "pgX45Ta4Dpmy+fFBTP+PQfbR/wUCe3W1HsYhvqRYa4B1j2Cl5NOCbTXBxQ5uB2VV\n" +
            "CJT65J84eiQuZBv/9ri1qu4GCVK44xUMy7KGm+qCjqkgZd/65MLNGnxUgh9Hqh2q\n" +
            "mfATMdy2qvKECcMmiaF7oBuxU/B8/Ik9NnnqdBvAH2fE8K9Ia3H2qGxX+qW4m8NF\n" +
            "mefN785CG2rCpeXoO0S3crgtuvnJU2YPy2UNYOVGukHeQAH/wEhfWVDOA/8fZYJE\n" +
            "PKKifwSTAgMBAAECggEBAIMSbF7TV13QH8kMoO2SN9ZwsGRfgSJE9MOkhG7ZOVhd\n" +
            "FvFI1g2MpByg2fhk4MIVVpzgfRdpaHTgw+UBfsg5icQ/hSgPUDBxHwYACa43lCUS\n" +
            "LHaHzTICUkGlUZTQtCm6ye5wx6UIvy2eUCrOrKk/SKgR9F/Aol6KZgEcgblszPDU\n" +
            "tAE5UfM/1npJLxgrZt9WJZGVILy4YJNLLFJSNYyGSTdWaQiPdq97wLIuMwt8/67R\n" +
            "6VCU5hrnAH7/6D08txQyV9xNbWqlnxS3annsKdpMbpoJGzNeN6yCWX8jMRTg4xwd\n" +
            "QcjNbHgjJlugfxbpzdwoUf4LiL9duBfZKrRO1luvbkECgYEA2bzAe0hplciZ9kJN\n" +
            "doD3cNZUP5q02PgDL42q4iruAurXbiUkG2M2vJ1/gVTvfwwaf3sc5f1iXpA7o7oM\n" +
            "GsaxcT/K7sUmcIJ6g4RZGwUsykR+BJHmwiGonbFyN+/o/WzxzWw8HVzvwYCEIbEm\n" +
            "x/Pw7mK4Q94JTuSIKsVP+0SEJGECgYEAxbWURzOafD7/HuCrM8Zd2lEgmiPX9mzc\n" +
            "LcGIQ9ymOhP+NnkLWvFU7YH8wULorfCqwEhn35HlOa0xrDE8WV32de3PLyn78e0O\n" +
            "LzxGq2K2T+MD+O7xtckje736mzuZr+vSes4w6byaBUu1DBFzbdMcB+64PVQYf80q\n" +
            "6pHLesmhzXMCgYEA18zlkMqSKyvovFO2Zq8njyQ919RDTY9xyN3F8ebOgwGyhq40\n" +
            "/Rf2FjabOtmtjOO4F4UzfRcHOeYF1h39BUTMdQve24t2r6gCOPshPVCBte7wXyMj\n" +
            "7GBjt/c41wvmhdZGSWzun8OOtN/lFWd4olC6B+q76jfUTebetlVdEhI0TcECgYEA\n" +
            "ja3VmECOkELEp+fwR6X7U5uXdV4CQQE5t0Bc2eSg6jWxkm+jh4QxjBH+gq/j9eHl\n" +
            "Ou2oIjp2vaIzWmXeDVycZV4Jfo7jkTaDV64mJmMSqqTQD++LHu4Ik4BMujk3pS2l\n" +
            "I4Pm4VjaNrlOAFvxD96c08JqHOjKtarc+kOIQXGElakCgYBZnF184JAwp4/MpEeX\n" +
            "tDiy4OPP9os3cxZ+NHLr/iUHVbOvDvmLZgV/kSBlu3/9PKITn20IiHsSZhOoCext\n" +
            "KKntX31Kr7AkXW5EDoUTv9LNiEaKVDc7vlBXwTSFiLM3sYrBvvaXR0rja+sQAvhf\n" +
            "OyXKYedmRjmsqT0Nje5lKac9Rw==\n" +
            "-----END PRIVATE KEY-----";

    @Inject
    ConfigService configService;
    @Inject
    SecretStore secretStore;

    @Inject
    CertificateGrantTokenRequestBuilder payloadBuilder;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GithubAccessTokenResponseParserImpl responseParser;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        SourceAuthModule.class,
        TestModules.ForFixedClock.class,
        TestModules.ForFixedUUID.class,
        MockModules.ForConfigService.class,
        MockModules.ForSecretStore.class,
    })
    public interface Container {
        void inject(CertificateGrantTokenRequestBuilderTest test);

        void inject(GithubAccessTokenResponseParserImpl test);
    }

    @BeforeAll
    public static void staticSetup() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @BeforeEach
    public void setup() {
        CertificateGrantTokenRequestBuilderTest.Container container =
                DaggerCertificateGrantTokenRequestBuilderTest_Container.create();
        container.inject(this);

        when(secretStore.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID))
                .thenReturn(clientId);
        when(configService.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT))
                .thenReturn(tokenEndpoint);
    }

    @SneakyThrows
    @Test
    public void tokenRequestPayload_with_jwt() {
        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
                .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(EXAMPLE_PRIVATE_KEY).build()));

        final String EXPECTED_ASSERTION = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJodHRwczovL2FwaS5naXRodWIuY29tL2FwcC9pbnN0YWxsYXRpb25zLzM5NDk0MzMxL2FjY2Vzc190b2tlbnMiLCJleHAiOjE2Mzk1MjY3MDAsImlhdCI6MTYzOTUyNjQwMCwiaXNzIjoiMzU5MDUwIiwianRpIjoiODg2Y2QyZDEtMmExZC00M2U5LTkxZDQtNmEyYjE2NmRmZjllIiwic3ViIjoiMzU5MDUwIn0.KErZcIn-BE618TfVpvLOcgE6lFfE8E6oCF-1IY6PsMd5Zf1QID12299Uv1ehFj-vO61IMyzWREcsB7AN81jDxmmG5bnx1-WZjsJ5d22bXCgY5CtVf17HMSx3l34kXL2LN2IqHUms21ks2bWxZ8YyjKONHHhxrVNoodQSoEw_fOhTDGkZ2_aGDg9W_gIhKqv38XM1utAErZWbNhh0eLNRDawtg88wa5nZSjmH74qty8xlxXmLIBJlaByGD-6ZfsI6AfUjJnLT7iK_Eu_fUbpQdVpfJl8GmeuGioWkGAL0cQZQ8p96yaWNdwVK2dMne8-XEbXvmcLc2UK0sPvoZm1LcQ";
        HttpHeaders httpHeaders = new HttpHeaders();
        payloadBuilder.addHeaders(httpHeaders);

        assertNotNull(httpHeaders.getAuthorization());
        assertEquals(EXPECTED_ASSERTION, httpHeaders.getAuthorization());
    }

    @Disabled // around for integration testing; don't want it for usual CI
    @SneakyThrows
    @Test
    public void integration() {
        // (can get out of AWS SystemParameter after tf apply)
        final String PRIVATE_KEY_FOR_INTEGRATION = "-----BEGIN PRIVATE KEY-----\n" +
                "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCoKLCwpGgaicC2\n" +
                "FAFvLL6J6GPkwdm7PkriVWfRrrSHzXDlFRcCbxy6HC/50fNV+2IHs/8EaudUfoNR\n" +
                "pgX45Ta4Dpmy+fFBTP+PQfbR/wUCe3W1HsYhvqRYa4B1j2Cl5NOCbTXBxQ5uB2VV\n" +
                "CJT65J84eiQuZBv/9ri1qu4GCVK44xUMy7KGm+qCjqkgZd/65MLNGnxUgh9Hqh2q\n" +
                "mfATMdy2qvKECcMmiaF7oBuxU/B8/Ik9NnnqdBvAH2fE8K9Ia3H2qGxX+qW4m8NF\n" +
                "mefN785CG2rCpeXoO0S3crgtuvnJU2YPy2UNYOVGukHeQAH/wEhfWVDOA/8fZYJE\n" +
                "PKKifwSTAgMBAAECggEBAIMSbF7TV13QH8kMoO2SN9ZwsGRfgSJE9MOkhG7ZOVhd\n" +
                "FvFI1g2MpByg2fhk4MIVVpzgfRdpaHTgw+UBfsg5icQ/hSgPUDBxHwYACa43lCUS\n" +
                "LHaHzTICUkGlUZTQtCm6ye5wx6UIvy2eUCrOrKk/SKgR9F/Aol6KZgEcgblszPDU\n" +
                "tAE5UfM/1npJLxgrZt9WJZGVILy4YJNLLFJSNYyGSTdWaQiPdq97wLIuMwt8/67R\n" +
                "6VCU5hrnAH7/6D08txQyV9xNbWqlnxS3annsKdpMbpoJGzNeN6yCWX8jMRTg4xwd\n" +
                "QcjNbHgjJlugfxbpzdwoUf4LiL9duBfZKrRO1luvbkECgYEA2bzAe0hplciZ9kJN\n" +
                "doD3cNZUP5q02PgDL42q4iruAurXbiUkG2M2vJ1/gVTvfwwaf3sc5f1iXpA7o7oM\n" +
                "GsaxcT/K7sUmcIJ6g4RZGwUsykR+BJHmwiGonbFyN+/o/WzxzWw8HVzvwYCEIbEm\n" +
                "x/Pw7mK4Q94JTuSIKsVP+0SEJGECgYEAxbWURzOafD7/HuCrM8Zd2lEgmiPX9mzc\n" +
                "LcGIQ9ymOhP+NnkLWvFU7YH8wULorfCqwEhn35HlOa0xrDE8WV32de3PLyn78e0O\n" +
                "LzxGq2K2T+MD+O7xtckje736mzuZr+vSes4w6byaBUu1DBFzbdMcB+64PVQYf80q\n" +
                "6pHLesmhzXMCgYEA18zlkMqSKyvovFO2Zq8njyQ919RDTY9xyN3F8ebOgwGyhq40\n" +
                "/Rf2FjabOtmtjOO4F4UzfRcHOeYF1h39BUTMdQve24t2r6gCOPshPVCBte7wXyMj\n" +
                "7GBjt/c41wvmhdZGSWzun8OOtN/lFWd4olC6B+q76jfUTebetlVdEhI0TcECgYEA\n" +
                "ja3VmECOkELEp+fwR6X7U5uXdV4CQQE5t0Bc2eSg6jWxkm+jh4QxjBH+gq/j9eHl\n" +
                "Ou2oIjp2vaIzWmXeDVycZV4Jfo7jkTaDV64mJmMSqqTQD++LHu4Ik4BMujk3pS2l\n" +
                "I4Pm4VjaNrlOAFvxD96c08JqHOjKtarc+kOIQXGElakCgYBZnF184JAwp4/MpEeX\n" +
                "tDiy4OPP9os3cxZ+NHLr/iUHVbOvDvmLZgV/kSBlu3/9PKITn20IiHsSZhOoCext\n" +
                "KKntX31Kr7AkXW5EDoUTv9LNiEaKVDc7vlBXwTSFiLM3sYrBvvaXR0rja+sQAvhf\n" +
                "OyXKYedmRjmsqT0Nje5lKac9Rw==\n" +
                "-----END PRIVATE KEY-----\n";

        when(secretStore.getConfigPropertyWithMetadata(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.PRIVATE_KEY))
                .thenReturn(Optional.of(ConfigService.ConfigValueWithMetadata.builder().value(PRIVATE_KEY_FOR_INTEGRATION).build()));

        HttpRequestFactory requestFactory = (new NetHttpTransport()).createRequestFactory();

        payloadBuilder.clock = Clock.systemUTC();
        responseParser.clock = Clock.systemUTC();

        HttpRequest request = requestFactory.buildPostRequest(new GenericUrl(tokenEndpoint), payloadBuilder.buildPayload());
        payloadBuilder.addHeaders(request.getHeaders());

        HttpResponse response = request.execute();
        assertEquals(201, response.getStatusCode());

        CanonicalOAuthAccessTokenResponseDto dto = responseParser.parseTokenResponse(response);

        assertNotNull(dto.getAccessToken());
        assertNotNull(dto.getExpiresIn());
        // 1 hour of expiration after requesting the token, but leaving a bit less for testing purposes
        assertTrue(dto.getExpiresIn() > 3000);
        assertEquals("Bearer", dto.getTokenType());
    }
}
