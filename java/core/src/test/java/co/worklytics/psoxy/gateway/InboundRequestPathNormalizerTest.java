package co.worklytics.psoxy.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InboundRequestPathNormalizerTest {

    InboundRequestPathNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new InboundRequestPathNormalizer();
        normalizer.apiModeConfig = ApiModeConfig.builder()
            .requestPathPrefixToTrim("/v1/")
            .build();
        normalizer.hostEnvironment = () -> "psoxy-gcal";
    }

    @ParameterizedTest
    @CsvSource({
        "/v1/psoxy-gcal/users, /v1/, /psoxy-gcal/users",
        "/v1/psoxy-gcal/users, v1/, /psoxy-gcal/users",
        "/psoxy-gcal/users, /v1/, /psoxy-gcal/users",
        "/v1/, /v1/, /",
        "/v1, /v1, /",
    })
    void stripLeadingPathPrefix(String path, String prefix, String expected) {
        assertEquals(expected, normalizer.stripLeadingPathPrefix(path, prefix));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  ", "\t"})
    void stripLeadingPathPrefix_blankPrefixIsNoOp(String prefix) {
        assertEquals("/api/v1/users", normalizer.stripLeadingPathPrefix("/api/v1/users", prefix));
    }

    @Test
    void normalize_trimsPrefixBeforeFunctionName() {
        assertEquals("/users", normalizer.normalize("/v1/psoxy-gcal/users"));
    }

    @Test
    void normalize_withoutPrefix() {
        normalizer.apiModeConfig = ApiModeConfig.builder().build();
        assertEquals("/users", normalizer.normalize("/psoxy-gcal/users"));
    }

    @Test
    void normalize_whenPrefixNotConfigured_doesNotStripV1Segment() {
        normalizer.apiModeConfig = ApiModeConfig.builder().build();
        assertEquals("/v1/users", normalizer.normalize("/v1/users"));
        assertEquals("/users", normalizer.normalize("/psoxy-gcal/users"));
        assertEquals("/v1/users", normalizer.normalize("/v1/psoxy-gcal/users"));
    }

    @Test
    void normalize_whenPrefixEmpty_doesNotStripV1Segment() {
        normalizer.apiModeConfig = ApiModeConfig.builder().requestPathPrefixToTrim("").build();
        assertEquals("/v1/users", normalizer.normalize("/v1/users"));
        assertEquals("/users", normalizer.normalize("/psoxy-gcal/users"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "  ", "\t"})
    void normalize_whenPrefixBlank_doesNotStripV1Segment(String blankPrefix) {
        normalizer.apiModeConfig = ApiModeConfig.builder().requestPathPrefixToTrim(blankPrefix).build();
        assertEquals("/v1/users", normalizer.normalize("/v1/users"));
        assertEquals("/users", normalizer.normalize("/psoxy-gcal/users"));
    }
}
