package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.SanitizerFactory;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CSVFileHandlerTest {

    @Inject
    CSVFileHandler csvFileHandler;

    @Inject
    SanitizerFactory sanitizerFactory;

    @Singleton
    @Component(modules = {
            PsoxyModule.class,
            TestModules.ForFixedClock.class,
            TestModules.ForFixedUUID.class,
            MockModules.ForConfigService.class,
    })
    public interface Container {
        void inject(CSVFileHandlerTest test);
    }

    @BeforeEach
    public void setup() {
        Container container = DaggerCSVFileHandlerTest_Container.create();
        container.inject(this);
    }

    @Test
    @SneakyThrows
    void handle_pseudonymize() {
        final String EXPECTED = "employeeId,email,department\r\n" +
                "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n" +
                "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",Sales\r\n" +
                "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\",Engineering\r\n" +
                "4,,Engineering\r\n"; //blank ID

        Sanitizer sanitizer = sanitizerFactory.create(Sanitizer.Options.builder()
                .rules(Rules.builder()
                        .pseudonymization(Rules.Rule.builder()
                                .csvColumns(Collections.singletonList("email"))
                                .build())
                        .build())
                .pseudonymizationSalt("salt")
                .defaultScopeId("hris")
                .build());

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = csvFileHandler.handle(in, sanitizer);

            assertEquals(EXPECTED, new String(result));
        }
    }

    @Test
    @SneakyThrows
    void handle_redaction() {
        final String EXPECTED = "employeeId,email\r\n" +
                "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\"\r\n" +
                "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\"\r\n" +
                "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\"\r\n" +
                "4,\r\n"; //blank ID

        Sanitizer sanitizer = sanitizerFactory.create(Sanitizer.Options.builder()
                .rules(Rules.builder()
                        .pseudonymization(Rules.Rule.builder()
                                .csvColumns(Collections.singletonList("email"))
                                .build())
                        .redaction(Rules.Rule.builder()
                                .csvColumns(Collections.singletonList("department"))
                                .build())
                        .build())
                .pseudonymizationSalt("salt")
                .defaultScopeId("hris")
                .build());

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = csvFileHandler.handle(in, sanitizer);

            assertEquals(EXPECTED, new String(result));
        }
    }

    @Test
    @SneakyThrows
    void handle_cased() {
        final String EXPECTED = "Employee Id,Email,Some Department\r\n" +
                "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n";

        Sanitizer sanitizer = sanitizerFactory.create(Sanitizer.Options.builder()
                .rules(Rules.builder()
                        .pseudonymization(Rules.Rule.builder()
                                .csvColumns(Arrays.asList("Employee Id", "Email"))
                                .build())
                        .build())
                .pseudonymizationSalt("salt")
                .defaultScopeId("hris")
                .build());

        File inputFile = new File(getClass().getResource("/csv/hris-example-headers-w-spaces.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = csvFileHandler.handle(in, sanitizer);

            assertEquals(EXPECTED, new String(result));
        }
    }
}
