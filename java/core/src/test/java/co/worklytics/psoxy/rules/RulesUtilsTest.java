package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.BulkModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.storage.StorageHandler;
import com.avaulta.gateway.rules.ColumnarRules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RulesUtilsTest {

    @Inject RulesUtils utils;
    @Inject @Named("ForYAML")
    ObjectMapper yamlMapper;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
    })
    public interface Container {
        void inject( RulesUtilsTest test);
    }

    @BeforeEach
    public void setup() {
        Container container = DaggerRulesUtilsTest_Container.create();
        container.inject(this);
    }

    static final String BASE64_YAML_REST = "YWxsb3dBbGxFbmRwb2ludHM6IGZhbHNlCmVuZHBvaW50czoKICAtIHBhdGhSZWdleDogL2NhbGVuZGFyL3YzL2NhbGVuZGFycy8uKi9ldmVudHMuKgogICAgdHJhbnNmb3JtczoKICAgICAgLSAhPHBzZXVkb255bWl6ZT4KICAgICAgICBqc29uUGF0aHM6CiAgICAgICAgICAtICQuLmVtYWlsCiAgICAgIC0gITxyZWRhY3Q+CiAgICAgICAganNvblBhdGhzOgogICAgICAgICAgLSAkLi5kaXNwbGF5TmFtZQogICAgICAgICAgLSAkLml0ZW1zWypdLmV4dGVuZGVkUHJvcGVydGllcy5wcml2YXRlCg==";
    static final String YAML_REST =
            "allowAllEndpoints: false\n" +
            "endpoints:\n" +
            "  - pathRegex: \"^/(v1.0|beta)/users/?[^/]*\"\n" +
            "    transforms:\n" +
            "    - !<redact>\n" +
            "      jsonPaths:\n" +
            "      - \"$..displayName\"\n" +
            "      - \"$..employeeId\"\n";

    static final String YAML_CSV =
        "columnsToPseudonymize:\n" +
        "  - \"EMPLOYEE_EMAIL\"\n" +
        "columnsToRedact:\n" +
        "  - \"MANAGEREMAIL\"\n";
    static final String BASE64_YAML_CSV = "Y29sdW1uc1RvUHNldWRvbnltaXplOgogIC0gIkVNUExPWUVFX0VNQUlMIgpjb2x1bW5zVG9SZWRhY3Q6CiAgLSAiTUFOQUdFUkVNQUlMIgo=";

    @ParameterizedTest
    @ValueSource(strings = {
        BASE64_YAML_REST,
        YAML_REST,
        YAML_CSV,
        BASE64_YAML_CSV,
    })
    void getRulesFromConfig(String encoded) {

        ConfigService config = mock(ConfigService.class);
        when(config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
            .thenReturn(Optional.of(encoded));

        RuleSet rules = utils.getRulesFromConfig(config).get();
        assertNotNull(rules);
    }


    List<StorageHandler.ObjectTransform> transformList =
        ImmutableList.<StorageHandler.ObjectTransform>builder()
            .add(StorageHandler.ObjectTransform.builder()
                .destinationBucketName("blah")
                .rules(CsvRules.builder()
                .columnToPseudonymize("something")
                .build())
                .build())
            .build();


    @SneakyThrows
    @Test
    public void parseYamlRulesFromConfig() {

        ConfigService config = mock(ConfigService.class);
        when(config.getConfigPropertyAsOptional(eq(BulkModeConfigProperty.ADDITIONAL_TRANSFORMS)))
            .thenReturn(Optional.of(yamlMapper.writeValueAsString(transformList)));

        assertEquals("blah",
            utils.parseAdditionalTransforms(config).get(0).getDestinationBucketName());
        assertEquals("something",
            ((CsvRules) utils.parseAdditionalTransforms(config).get(0).getRules()).getColumnsToPseudonymize().get(0));
    }
}
