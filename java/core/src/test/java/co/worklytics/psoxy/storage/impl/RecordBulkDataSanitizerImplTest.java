package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SecretStore;
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

    java.util.function.Supplier<OutputStream> outputStreamSupplier;

    ByteArrayOutputStream outputStream;

    // to cover both rules versions, calling this inside of each test with different rules to set up
    // with that rule set at run time
    void setUpWithRules(String rawRules) {
        this.rawRules = rawRules;

        RecordBulkDataSanitizerImplTest.Container container = DaggerRecordBulkDataSanitizerImplTest_Container.create();
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

        void inject(RecordBulkDataSanitizerImplTest test);

        @Module
        interface ForConfigService {
            @Provides
            @Singleton
            static ConfigService configService() {
                ConfigService mock = MockModules.provideMock(ConfigService.class);
                when(mock.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
                    .thenReturn(Optional.of(rawRules));
                return mock;
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
        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(pathToOriginal),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray());
        assertEquals(new String(TestUtils.getData(pathToSanitized)), output);

        //TODO: fix failing test

        // encoder.encode(Pseudonym.builder().hash(DigestUtils.sha256("5" + "salt")).build()) --> t~cMWVVout6L1o-OKqU9a0Z1Sfqqg_i5J_zzU0M2EfDJg
        // encoder.encode(Pseudonym.builder().hash(DigestUtils.sha256("2" + "salt")).build()) --> t~-hN_i1M1DeMAicDVp6LhFgW9lH7r3_LbOpTlXYWpXVI

        //failing bc -sanitized has values OTHER than those:
        //"t~bRA7u8gkRFVS4a7u1BzBPsFttCTbbW7ICsUN2N9CVso"
        //"t~JoRciV9pgnaBd-s4kJk8yF2WsYtl8l8a3Gi4fTOnpw0"

        // why???
    }


    @Test
    void noTransforms() throws IOException {
        this.setUpWithRules("---\n" +
            "format: \"NDJSON\"\n" +
            "transforms:\n");

        final String objectPath = "export-20231128/file.ndjson";
        final String pathToOriginal = "bulk/example.ndjson";
        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(pathToOriginal),
            outputStreamSupplier);


        String output = new String(outputStream.toByteArray());

        final String EXPECTED = "{\"foo\":1,\"bar\":2,\"other\":\"three\"}\n" +
            "{\"foo\":4,\"bar\":5,\"other\":\"six\"}\n";
        assertEquals(EXPECTED, output);
    }

}
