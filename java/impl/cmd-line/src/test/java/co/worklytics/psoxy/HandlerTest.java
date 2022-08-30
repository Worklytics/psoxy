package co.worklytics.psoxy;

import co.worklytics.test.TestModules;
import dagger.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class HandlerTest {


    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        TestModules.ForConfigService.class,
    })
    public interface Container {
        void inject(HandlerTest test);
    }


    @Inject
    Handler handler;

    @BeforeEach
    public void setup() {
        Container container = DaggerHandlerTest_Container.create();
        container.inject(this);
    }

    @Test
    void main() {
        final String EXPECTED = "employeeId,email,department\r\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"wdxMApbuV7MglPNkZrM2WdV_v6x5Z31k8VbmqFCPRZI\"\"}\",Engineering\r\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"usvAqWmh_e2iweTO3zC6KIRxZthJcyBHkb9qqaH9PSw\"\"}\",Sales\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"Kxsm-xl6Y7fD15XEnp0fBbjGiVWEo90yBjhhQLqcXrI\"\"}\",Engineering\r\n" +
            "4,,Engineering\r\n"; //blank ID


        Config config = new Config();
        config.pseudonymizationSalt = "salt";
        config.columnsToPseudonymize = Collections.singleton("email");

        File inputFile = new File(getClass().getResource("/hris-example.csv").getFile());

        StringWriter s = new StringWriter();
        handler.pseudonymize(config, inputFile, s);


        assertEquals(EXPECTED, s.toString());
    }


    @Test
    void main_redaction() {
        final String EXPECTED = "employeeId,email\r\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"wdxMApbuV7MglPNkZrM2WdV_v6x5Z31k8VbmqFCPRZI\"\"}\"\r\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"usvAqWmh_e2iweTO3zC6KIRxZthJcyBHkb9qqaH9PSw\"\"}\"\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"Kxsm-xl6Y7fD15XEnp0fBbjGiVWEo90yBjhhQLqcXrI\"\"}\"\r\n" +
            "4,\r\n"; //blank ID

        Config config = new Config();
        config.pseudonymizationSalt = "salt";
        config.columnsToRedact = Collections.singleton("department");
        config.columnsToPseudonymize = Collections.singleton("email");

        File inputFile = new File(getClass().getResource("/hris-example.csv").getFile());

        StringWriter s = new StringWriter();
        handler.pseudonymize(config, inputFile, s);

        assertEquals(EXPECTED, s.toString());
    }

    @Test
    void main_cased() {
        final String EXPECTED = "Employee Id,Email,Some Department\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"pLl3XK16GbhWPs9BmUho9Q73VAOllCeIsVQQMFvnYr4\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"wdxMApbuV7MglPNkZrM2WdV_v6x5Z31k8VbmqFCPRZI\"\"}\",Engineering\r\n";

        Config config = new Config();
        config.pseudonymizationSalt = "salt";
        config.columnsToPseudonymize = Set.of("Employee Id","Email");
        config.defaultScopeId = "hris";

        File inputFile = new File(getClass().getResource("/hris-example-headers-w-spaces.csv").getFile());

        StringWriter s = new StringWriter();
        handler.pseudonymize(config, inputFile, s);

        assertEquals(EXPECTED, s.toString());
    }

}
