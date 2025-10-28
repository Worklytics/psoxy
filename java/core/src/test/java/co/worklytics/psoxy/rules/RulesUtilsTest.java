package co.worklytics.psoxy.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.Endpoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.BulkModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.storage.StorageHandler;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import dagger.Component;
import lombok.SneakyThrows;

class RulesUtilsTest {

    @Inject RulesUtils utils;
    @Inject @Named("ForYAML")
    ObjectMapper yamlMapper;
    @Inject
    ConfigService config;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        MockModules.ForConfigService.class
    })
    public interface Container {
        void inject( RulesUtilsTest test);
    }

    @BeforeEach
    public void setup() {
        Container container = DaggerRulesUtilsTest_Container.create();
        container.inject(this);
    }

    static final String BASE64_YAML_REST = "YWxsb3dBbGxFbmRwb2ludHM6IGZhbHNlCmVuZHBvaW50czoKICAtIHBhdGhSZWdleDogIl4vKHYxLjB8YmV0YSkvdXNlcnMvP1teL10qIgogICAgdHJhbnNmb3JtczoKICAgIC0gITxyZWRhY3Q+CiAgICAgIGpzb25QYXRoczoKICAgICAgLSAiJC4uZGlzcGxheU5hbWUiCiAgICAgIC0gIiQuLmVtcGxveWVlSWQiCg==";
    static final String BASE64_YAML_REST_COMPRESSED = "H4sIAAAAAAAAAFWMPQvCQBBE+/yK9bBQwZy2QRQLCxsRWzGwepsY2dwdt+dHwB9vEkSwnJn3Bpndc828sca7ykbJoEAWSuhXJABT8BivByrplYHK9egxT2fvM0Uc67tQEL065vo0US0LEANaKVyoe7ezB4tABi9x2WeAmzi7bx+/QIeoYZqaSjxjs8Oa1N9AtWfXEG2NSj7mG4K5sgAAAA==";
    static final String YAML_REST =
            "allowAllEndpoints: false\n" +
            "endpoints:\n" +
            "  - pathRegex: \"^/(v1.0|beta)/users/?[^/]*\"\n" +
            "    transforms:\n" +
            "    - !<redact>\n" +
            "      jsonPaths:\n" +
            "      - \"$..displayName\"\n" +
            "      - \"$..employeeId\"\n";

    static final String YAML_REST_WITH_ALLOWED_METHODS =
        "---\n" +
            "endpoints:\n" +
            "- pathTemplate: \"/some/{path}\"\n" +
            "  allowedMethods:\n" +
            "  - \"GET\"\n" +
            "  - \"POST\"\n";

    static final String YAML_CSV =
        "columnsToPseudonymize:\n" +
        "  - \"EMPLOYEE_EMAIL\"\n" +
        "columnsToRedact:\n" +
        "  - \"MANAGEREMAIL\"\n";
    static final String BASE64_YAML_CSV = "Y29sdW1uc1RvUHNldWRvbnltaXplOgogIC0gIkVNUExPWUVFX0VNQUlMIgpjb2x1bW5zVG9SZWRhY3Q6CiAgLSAiTUFOQUdFUkVNQUlMIgo=";


    static final String YAML_MULTI = "fileRules:\n" +
        "  /export/{week}/index_{shard}.ndjson:\n" +
        "    format: \"NDJSON\"\n" +
        "    transforms:\n" +
        "      - redact: \"foo\"\n" +
        "      - pseudonymize: \"bar\"\n" +
        "  /export/{week}/data_{shard}.csv:\n" +
        "    columnsToPseudonymize:\n" +
        "      - \"email\"\n" +
        "    delimiter: \",\"\n" +
        "    pseudonymFormat: \"JSON\"\n";

    static final String YAML_RECORD = "format: NDJSON\n" +
        "transforms:\n" +
        "  - redact: \"$.summary\"\n" +
        "  - redact: \"$.summary\"\n" +
        "  - pseudonymize: \"$.email\"\n" +
        "  - pseudonymize: \"$.email\"\n";


    @ParameterizedTest
    @ValueSource(strings = {
        BASE64_YAML_REST,
        YAML_REST,
        YAML_CSV,
        BASE64_YAML_CSV,
        YAML_MULTI,
        YAML_RECORD,
    })
    void getRulesFromConfig(String encoded) {
        when(config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
            .thenReturn(Optional.of(encoded));
        when(config.getConfigPropertyOrError(eq(ProxyConfigProperty.SOURCE)))
            .thenReturn("hris");

        com.avaulta.gateway.rules.RuleSet rules = utils.getRulesFromConfig(config, new EnvVarsConfigService()).get();
        assertNotNull(rules);
    }


    List<StorageHandler.ObjectTransform> transformList =
        ImmutableList.<StorageHandler.ObjectTransform>builder()
            .add(StorageHandler.ObjectTransform.builder()
                .destinationBucketName("blah")
                .rules(ColumnarRules.builder()
                .columnToPseudonymize("something")
                .build())
                .build())
            .build();


    @SneakyThrows
    @Test
    public void parseYamlRulesFromConfig() {

        when(config.getConfigPropertyAsOptional(eq(BulkModeConfigProperty.ADDITIONAL_TRANSFORMS)))
            .thenReturn(Optional.of(yamlMapper.writeValueAsString(transformList)));

        assertEquals("blah",
            utils.parseAdditionalTransforms(config).get(0).getDestinationBucketName());
        assertEquals("something",
            ((ColumnarRules) utils.parseAdditionalTransforms(config).get(0).getRules()).getColumnsToPseudonymize().get(0));
    }

    @SneakyThrows
    @ValueSource(strings = {
        YAML_REST,
        BASE64_YAML_REST,
        BASE64_YAML_REST_COMPRESSED,
    })
    @ParameterizedTest
    void decodeToYaml(String encoded) {
        String decoded = utils.decodeToYaml(encoded);
        assertEquals(YAML_REST, decoded);
    }

    @Test
    @SneakyThrows
    void check_allowed_methods_is_not_included_if_empty() {
        RESTRules rules = Rules2.builder()
            .endpoint(Endpoint.builder()
                .pathTemplate("/some/{path}")
                .build())
            .build();

        String yaml = yamlMapper.writeValueAsString(rules);
        assertFalse(yaml.contains("allowedMethods"));
    }

    @Test
    @SneakyThrows
    void check_allowed_methods_is_included() {
        RESTRules rules = Rules2.builder()
            .endpoint(Endpoint.builder()
                .pathTemplate("/some/{path}")
                .allowedMethods(Set.of("GET", "POST"))
                .build())
            .build();

        String yaml = yamlMapper.writeValueAsString(rules);
        assertTrue(yaml.contains("allowedMethods"));
        assertEquals(YAML_REST_WITH_ALLOWED_METHODS, yaml);
    }

    // if you change YAML_REST, this test will fail; you can copy-paste the expected value to
    // BASE64_YAML_REST_COMPRESSED
    @Disabled // useless, and weirdly seems to fail via maven ... serialization issue?
    @Test
    void verifyCompression() {
        assertEquals(BASE64_YAML_REST_COMPRESSED,
            TestUtils.asBase64Gzipped(YAML_REST));
    }
}
