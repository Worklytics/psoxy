package co.worklytics.psoxy.rules.workdata;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.storage.BulkDataTestUtils;
import co.worklytics.psoxy.storage.StorageHandler;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import co.worklytics.test.TestModules;
import com.avaulta.gateway.rules.MultiTypeBulkDataRules;
import com.avaulta.gateway.rules.RuleSet;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class WorkDataGenericTests {

    static String rulesPath;

    @Inject
    RuleSet rules;

    @Inject
    StorageHandler storageHandler;

    Supplier<OutputStream> outputStreamSupplier;

    ByteArrayOutputStream outputStream;

    void setUp(String rulesPath) {
        this.rulesPath = rulesPath;

        Container container = DaggerWorkDataGenericTests_Container.create();
        container.inject(this);

        outputStream = new ByteArrayOutputStream();
        outputStreamSupplier = () -> outputStream;
    }

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        ConfigRulesModule.class,
        Container.ForConfigService.class,
        MockModules.ForSecretStore.class,
        MockModules.ForHostEnvironment.class,
        MockModules.ForResourceService.class,
        TestModules.ForProxyConstants.class,
    })
    public interface Container {
        void inject(WorkDataGenericTests test);

        @Module
        interface ForConfigService {
            @Provides
            @Singleton
            static ConfigService configService() {
                ConfigService mock = MockModules.provideMock(ConfigService.class);
                when(mock.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
                    .thenReturn(Optional.of(new String(TestUtils.getData(rulesPath), StandardCharsets.UTF_8)));
                return mock;
            }
        }
    }

    @ValueSource(
        strings = {
            "sources/workdata-generic/workdata-generic.yaml"
        }
    )
    @ParameterizedTest
    public void rulesValid(String rulesPath) {
        setUp(rulesPath);
        assertTrue(rules instanceof MultiTypeBulkDataRules);
    }

    @SneakyThrows
    @CsvSource({
        "sources/workdata-generic/workdata-generic.yaml,events0.ndjson",
        "sources/workdata-generic/workdata-generic.yaml,items0.ndjson",
        "sources/workdata-generic/workdata-generic.yaml,accounts0.ndjson"
    })
    @ParameterizedTest
    public void files(String rulesPath, String file) {
        setUp(rulesPath);
        final String objectPath = "export-20231128/" + file;
        final String pathToOriginal = "sources/workdata-generic/example-bulk/original/" + file;
        final String pathToSanitized = "sources/workdata-generic/example-bulk/sanitized/" + file;
        
        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(pathToOriginal),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(new String(TestUtils.getData(pathToSanitized), StandardCharsets.UTF_8), output );
    }
}
