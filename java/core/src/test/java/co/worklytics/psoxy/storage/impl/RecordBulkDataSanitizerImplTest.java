package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.rules.slack.DaggerSlackDiscoveryBulkTests_Container;
import co.worklytics.psoxy.rules.slack.SlackDiscoveryBulkTests;
import co.worklytics.psoxy.storage.BulkDataTestUtils;
import co.worklytics.psoxy.storage.StorageHandler;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.rules.RuleSet;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class RecordBulkDataSanitizerImplTest {

    static String rawRules;


    @Inject
    RuleSet rules;

    @Inject
    StorageHandler storageHandler;

    Writer writer;
    ByteArrayOutputStream outputStream;

    // to cover both rules versions, calling this inside of each test with different rules to set up
    // with that rule set at run time
    void setUpWithRules(String rawRules) {
        this.rawRules = rawRules;

        RecordBulkDataSanitizerImplTest.Container container = DaggerRecordBulkDataSanitizerImplTest_Container.create();
        container.inject(this);

        outputStream = new ByteArrayOutputStream();
        writer = new BufferedWriter(new OutputStreamWriter(outputStream));
    }

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        ConfigRulesModule.class,
        Container.ForConfigService.class
    })
    public interface Container {

        void inject(RecordBulkDataSanitizerImplTest test);

        @Module
        interface ForConfigService {
            @Provides
            @Singleton
            static ConfigService configService() {
                ConfigService mock = MockModules.provideMock(ConfigService.class);
                when(mock.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
                    .thenReturn(Optional.of(rawRules));
                when(mock.getConfigPropertyAsOptional(eq(ProxyConfigProperty.PSOXY_SALT)))
                    .thenReturn(Optional.of("salt"));

                return mock;
            }

            @Provides @Singleton
            static HostEnvironment hostEnvironment() {
                return MockModules.provideMock(HostEnvironment.class);
            }
        }

    }

    @SneakyThrows
    @Test
    void base() {
        this.setUpWithRules("---\n" +
            "format: \"NDJSON\"\n" +
            "transforms:\n" +
            "- redact: \"foo\"\n" +
            "- pseudonymize: \"bar\"\n");


        final String objectPath = "export-20231128/file.ndjson";
        final String pathToOriginal = "bulk/example.ndjson";
        final String pathToSanitized = "bulk/example-sanitized.ndjson";
        storageHandler.handle(BulkDataTestUtils.request(objectPath, pathToOriginal, writer), rules);

        writer.close();

        String output = new String(outputStream.toByteArray());
        assertEquals(new String(TestUtils.getData(pathToSanitized)), output);
    }


    @Test
    void noTransforms() throws IOException {
        this.setUpWithRules("---\n" +
            "format: \"NDJSON\"\n" +
            "transforms:\n");

        final String objectPath = "export-20231128/file.ndjson";
        final String pathToOriginal = "bulk/example.ndjson";
        final String pathToSanitized = "bulk/example-sanitized.ndjson";
        storageHandler.handle(BulkDataTestUtils.request(objectPath, pathToOriginal, writer), rules);

        writer.close();

        String output = new String(outputStream.toByteArray());

        final String EXPECTED = "{\"foo\":1,\"bar\":2,\"other\":\"three\"}\n" +
            "{\"foo\":4,\"bar\":5,\"other\":\"six\"}\n";
        assertEquals(EXPECTED, output);
    }

}
