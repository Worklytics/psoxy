package co.worklytics.psoxy.rules.glean;

import co.worklytics.psoxy.ConfigRulesModule;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.storage.BulkDataTestUtils;
import co.worklytics.psoxy.storage.StorageHandler;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.rules.RecordRules;
import com.avaulta.gateway.rules.RuleSet;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class GleanCustomerEventsLogBulkTests {

    static String rulesPath;

    @Inject
    RuleSet rules;

    @Inject
    StorageHandler storageHandler;

    Supplier<OutputStream> outputStreamSupplier;

    ByteArrayOutputStream outputStream;

    void setUp(String rulesPath) {
        this.rulesPath = rulesPath;

        Container container = DaggerGleanCustomerEventsLogBulkTests_Container.create();
        container.inject(this);

        outputStream = new ByteArrayOutputStream();
        outputStreamSupplier = () -> outputStream;
    }

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        MockModules.ForOpenNlp.class,
        TestModules.ForApiModeConfig.class,
        ConfigRulesModule.class,
        Container.ForConfigService.class,
        MockModules.ForSecretStore.class,
        MockModules.ForHostEnvironment.class,
        MockModules.ForResourceService.class,
        TestModules.ForProxyConstants.class,
    })
    public interface Container {
        void inject(GleanCustomerEventsLogBulkTests test);

        @Module
        interface ForConfigService {
            @Provides
            @Singleton
            static ConfigService configService() {
                ConfigService mock = MockModules.provideMock(ConfigService.class);
                when(mock.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
                    .thenAnswer(invocation -> Optional.of(new String(
                        TestUtils.getData(rulesPath), StandardCharsets.UTF_8)));
                return mock;
            }
        }
    }

    @ValueSource(strings = {
        "sources/glean/glean-customer-events-log-bulk/glean-customer-events-log-bulk.yaml"
    })
    @ParameterizedTest
    public void rulesValid(String rulesPath) {
        setUp(rulesPath);
        assertTrue(rules instanceof RecordRules);
        RecordRules recordRules = (RecordRules) rules;
        assertTrue(recordRules.getOutputSchemaFilterOptional().isPresent());
    }

    @SneakyThrows
    @Test
    public void sampleNdjson() {
        setUp("sources/glean/glean-customer-events-log-bulk/glean-customer-events-log-bulk.yaml");

        final String objectPath = "glean-customer-event-firehose-stream-2024/sample.ndjson";
        final String pathToOriginal =
            "sources/glean/glean-customer-events-log-bulk/example-bulk/original/sample.ndjson";
        final String pathToSanitized =
            "sources/glean/glean-customer-events-log-bulk/example-bulk/sanitized/sample.ndjson";

        storageHandler.handle(BulkDataTestUtils.request(objectPath),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(pathToOriginal),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        assertEquals(new String(TestUtils.getData(pathToSanitized), StandardCharsets.UTF_8), output);
    }

    @SneakyThrows
    @Test
    public void llmCallEventPreservesTokenMetricsAndIntegerDepartmentId() {
        setUp("sources/glean/glean-customer-events-log-bulk/glean-customer-events-log-bulk.yaml");

        String llmEvent = "{\"timestamp\":\"2026-05-24T03:48:12.354000Z\",\"insertId\":\"llm-001\","
            + "\"receiveTimestamp\":\"2026-05-25T06:47:18.544045Z\",\"jsonPayload\":{\"Type\":\"LLM_CALL\","
            + "\"IsScrubbed\":false,\"User\":{\"Department\":\"Widget  QA\",\"DepartmentId\":52,"
            + "\"UserEmail\":\"alice@acme.com\",\"UserId\":\"5F9A6DF572F4577168C069CA725645D0\"},"
            + "\"LlmCall\":{\"InputTokens\":31144,\"OutputTokens\":201,\"CacheReadInputTokens\":30464,"
            + "\"Model\":\"GPT5_4\",\"Provider\":\"OPEN_AI\",\"WorkflowRunId\":\"541f42b9f9d346adf65405406b2ef5143a\"}}}"
            + System.lineSeparator();

        storageHandler.handle(BulkDataTestUtils.request("llm.ndjson"),
            BulkDataTestUtils.transform(rules),
            () -> new ByteArrayInputStream(llmEvent.getBytes(StandardCharsets.UTF_8)),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(output.contains("\"receiveTimestamp\":\"2026-05-25T06:47:18.544045Z\""));
        assertTrue(output.contains("\"DepartmentId\":52"));
        assertTrue(output.contains("\"InputTokens\":31144"));
        assertTrue(output.contains("\"WorkflowRunId\":\"541f42b9f9d346adf65405406b2ef5143a\""));
        assertFalse(output.contains("alice@acme.com"));
        assertTrue(output.contains("Widget  QA"));
    }

    @SneakyThrows
    @Test
    public void productionShapedEventsSanitized() {
        setUp("sources/glean/glean-customer-events-log-bulk/glean-customer-events-log-bulk.yaml");

        storageHandler.handle(BulkDataTestUtils.request("sample.ndjson"),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(
                "sources/glean/glean-customer-events-log-bulk/example-bulk/original/sample.ndjson"),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(output.contains("\"Type\":\"LLM_CALL\""));
        assertTrue(output.contains("\"Type\":\"ACTION\""));
        assertTrue(output.contains("\"Type\":\"MCP_USAGE\""));
        assertTrue(output.contains("\"Type\":\"AUTOCOMPLETE\""));
        assertTrue(output.contains("\"Type\":\"SEARCH\""));
        assertTrue(output.contains("\"Type\":\"GLEAN_BOT_ACTIVITY\""));
        assertTrue(output.contains("\"Type\":\"CLIENT_EVENT\""));
        assertTrue(output.contains("\"ActionId\":\"Glean Search\""));
        assertTrue(output.contains("\"ToolName\":\"search\""));
        assertTrue(output.contains("\"INIT_EPOCH_MS\":\"1724338092104\""));
        assertTrue(output.contains("\"UiElement\":\"chat-assistant-export-button\""));
        assertTrue(output.contains("\"DepartmentId\":0"));
        assertFalse(output.contains("user-llm@example.com"));
        assertFalse(output.contains("user-action@example.com"));
        assertFalse(output.contains("vacation carryover rules"));
        assertFalse(output.contains("acme api"));
        assertTrue(output.contains("Orbit Ops"));
        assertTrue(output.contains("Rivet Analytics"));
    }

    @SneakyThrows
    @Test
    public void dropsUnknownPayloadFields() {
        setUp("sources/glean/glean-customer-events-log-bulk/glean-customer-events-log-bulk.yaml");

        storageHandler.handle(BulkDataTestUtils.request("any-path.ndjson"),
            BulkDataTestUtils.transform(rules),
            BulkDataTestUtils.inputStreamSupplier(
                "sources/glean/glean-customer-events-log-bulk/example-bulk/original/sample.ndjson"),
            outputStreamSupplier);

        String output = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        assertFalse(output.contains("FuturePayload"));
        assertFalse(output.contains("SecretField"));
    }

}
