package co.worklytics.psoxy;

import co.worklytics.psoxy.storage.BulkDataSanitizerFactory;
import co.worklytics.psoxy.storage.impl.ColumnarBulkDataSanitizerImpl;
import co.worklytics.test.TestModules;
import com.avaulta.gateway.rules.ColumnarRules;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HandlerTest {


    Handler handler;
    CommandLineConfigService configService;

    @BeforeEach
    public void setup() {
        String[] args = new String[]{"--salt", "secret", "--pseudonymize", "email"};


        CmdLineContainer container = DaggerCmdLineContainer.builder()
            .cmdLineModule(new CmdLineModule(args))
            .build();
        handler = container.fileHandler();

        configService = container.commandLineConfigServiceFactory().create(args);

        ColumnarRules csvRules = ColumnarRules.builder()
            .build();

        //make this deterministic for testing
        ColumnarBulkDataSanitizerImpl example = (ColumnarBulkDataSanitizerImpl) handler.fileHandlerStrategy.get(csvRules);
        handler.fileHandlerStrategy = mock(BulkDataSanitizerFactory.class);
        when(handler.fileHandlerStrategy.get(any())).then(invocation -> {
            example.setRules(invocation.getArgument(0));
            example.setRecordShuffleChunkSize(1);
            return example;
        });
    }

    @Test
    void main() {
        final String EXPECTED = "employeeId,email,department\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"wdxMApbuV7MglPNkZrM2WdV_v6x5Z31k8VbmqFCPRZI\"\"}\",Engineering\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"usvAqWmh_e2iweTO3zC6KIRxZthJcyBHkb9qqaH9PSw\"\"}\",Sales\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"Kxsm-xl6Y7fD15XEnp0fBbjGiVWEo90yBjhhQLqcXrI\"\"}\",Engineering\n" +
            "4,,Engineering\n"; //blank ID



        File inputFile = new File(getClass().getResource("/hris-example.csv").getFile());

        StringWriter s = new StringWriter();

        handler.sanitize(configService.getCliConfig(), inputFile, s);
        assertEquals(EXPECTED, s.toString());
    }


    @Test
    void main_redaction() {
        final String EXPECTED = "employeeId,email\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"wdxMApbuV7MglPNkZrM2WdV_v6x5Z31k8VbmqFCPRZI\"\"}\"\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"usvAqWmh_e2iweTO3zC6KIRxZthJcyBHkb9qqaH9PSw\"\"}\"\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"Kxsm-xl6Y7fD15XEnp0fBbjGiVWEo90yBjhhQLqcXrI\"\"}\"\n" +
            "4,\n"; //blank ID

        Config config = new Config();
        config.columnsToRedact = Collections.singleton("department");
        config.columnsToPseudonymize = Collections.singleton("email");

        File inputFile = new File(getClass().getResource("/hris-example.csv").getFile());

        StringWriter s = new StringWriter();
        handler.sanitize(config, inputFile, s);

        assertEquals(EXPECTED, s.toString());
    }

    @Test
    void main_cased() {
        final String EXPECTED = "Employee Id,Email,Some Department\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"pLl3XK16GbhWPs9BmUho9Q73VAOllCeIsVQQMFvnYr4\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"wdxMApbuV7MglPNkZrM2WdV_v6x5Z31k8VbmqFCPRZI\"\"}\",Engineering\n";

        Config config = new Config();
        config.columnsToPseudonymize = Set.of("Employee Id","Email");
        config.defaultScopeId = "hris";

        File inputFile = new File(getClass().getResource("/hris-example-headers-w-spaces.csv").getFile());

        StringWriter s = new StringWriter();
        handler.sanitize(config, inputFile, s);

        assertEquals(EXPECTED, s.toString());
    }

}
