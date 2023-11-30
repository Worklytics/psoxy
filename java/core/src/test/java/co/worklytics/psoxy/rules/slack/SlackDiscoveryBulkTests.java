package co.worklytics.psoxy.rules.slack;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.storage.StorageHandler;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.rules.MultiTypeBulkDataRules;
import com.avaulta.gateway.rules.RuleSet;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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


    @Inject
    RuleSet rules;

    @Inject
    StorageHandler storageHandler;

    Writer writer;
    ByteArrayOutputStream outputStream;

    @BeforeEach
    public void setUp() {

        Container container = DaggerSlackDiscoveryBulkTests_Container.create();
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
        void inject(SlackDiscoveryBulkTests test);


        @Module
        interface ForConfigService {
            @Provides
            @Singleton
            static ConfigService configService() {
                ConfigService mock = MockModules.provideMock(ConfigService.class);
                when(mock.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
                    .thenReturn(Optional.of(new String(TestUtils.getData("sources/slack/discovery-bulk.yaml"))));
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


    @Test
    public void rulesValid() {
        assertTrue(rules instanceof MultiTypeBulkDataRules);
    }

    StorageEventRequest request(
            String sourceObjectPath,
            String filePath) {
         Reader reader = new InputStreamReader(new ByteArrayInputStream(TestUtils.getData(filePath)));

        return StorageEventRequest.builder()
            .sourceBucketName("bucket")
            .sourceObjectPath(sourceObjectPath)
            .sourceReader(reader)
            .destinationBucketName("bucket")
            .destinationObjectPath(sourceObjectPath)
            .destinationWriter(writer)
            .build();
    }

    @SneakyThrows
    @ValueSource(strings ={
        "channels.ndjson",
        "messages-D06TX2RP0.ndjson",
        "users.ndjson",
    })
    @ParameterizedTest
    public void files(String file) {
        final String objectPath = "/export-20231128/" + file + ".gz";
        final String pathToOriginal = "sources/slack/example-bulk/original/" + file;
        final String pathToSanitized = "sources/slack/example-bulk/sanitized/" + file;
        storageHandler.handle(request(objectPath, pathToOriginal), rules);

        writer.flush();
        writer.close();

        String output = new String(outputStream.toByteArray());
        assertEquals(new String(TestUtils.getData(pathToSanitized)), output);
    }
}
