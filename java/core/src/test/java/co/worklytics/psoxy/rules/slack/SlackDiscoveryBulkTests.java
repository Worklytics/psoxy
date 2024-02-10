package co.worklytics.psoxy.rules.slack;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.storage.BulkDataTestUtils;
import co.worklytics.psoxy.storage.StorageHandler;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class SlackDiscoveryBulkTests {

    static String rulesPath;


    @Inject
    RuleSet rules;

    @Inject
    StorageHandler storageHandler;

    java.util.function.Supplier<OutputStream> outputStreamSupplier;

    ByteArrayOutputStream outputStream;

    // to cover both rules versions, calling this inside of each test with different rules to set up
    // with that rule set at run time
    void setUp(String rulesPath) {
        this.rulesPath = rulesPath;

        Container container = DaggerSlackDiscoveryBulkTests_Container.create();
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
    })
    public interface Container {
        void inject(SlackDiscoveryBulkTests test);


        @Module
        interface ForConfigService {
            @Provides
            @Singleton
            static ConfigService configService() {
                ConfigService mock = MockModules.provideMock(ConfigService.class);
                when(mock.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
                    .thenReturn(Optional.of(new String(TestUtils.getData(rulesPath))));
                return mock;
            }
        }
    }


    @ValueSource(
        strings = {
            "sources/slack/discovery-bulk-hierarchical.yaml",
            "sources/slack/discovery-bulk.yaml"
        }

    )
    @ParameterizedTest
    public void rulesValid(String rulesPath) {
        setUp(rulesPath);
        assertTrue(rules instanceof MultiTypeBulkDataRules);
    }


    @SneakyThrows
    @CsvSource({
        "sources/slack/discovery-bulk-hierarchical.yaml,channels.ndjson",
        "sources/slack/discovery-bulk-hierarchical.yaml,messages-D06TX2RP0.ndjson",
        "sources/slack/discovery-bulk-hierarchical.yaml,users.ndjson",
        "sources/slack/discovery-bulk.yaml,channels.ndjson",
        "sources/slack/discovery-bulk.yaml,messages-D06TX2RP0.ndjson",
        "sources/slack/discovery-bulk.yaml,users.ndjson",
    })
    @ParameterizedTest
    public void files(String rulesPath, String file) {
        setUp(rulesPath);
        final String objectPath = "export-20231128/" + file + ".gz";
        final String pathToOriginal = "sources/slack/example-bulk/original/" + file;
        final String pathToSanitized = "sources/slack/example-bulk/sanitized/" + file;
        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(pathToOriginal),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray());
        assertEquals(new String(TestUtils.getData(pathToSanitized)), output);
    }
}
