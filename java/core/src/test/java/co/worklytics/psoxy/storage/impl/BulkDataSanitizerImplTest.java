package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.rules.*;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import dagger.Component;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileReader;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BulkDataSanitizerImplTest {

    @Inject
    ColumnarBulkDataSanitizerImpl columnarFileSanitizerImpl;

    @Inject
    RESTApiSanitizerFactory sanitizerFactory;

    @Inject
    RulesUtils rulesUtils;

    @Inject
    PseudonymizerImplFactory pseudonymizerImplFactory;

    Pseudonymizer pseudonymizer;

    @Singleton
    @Component(modules = {
            PsoxyModule.class,
            TestModules.ForFixedClock.class,
            TestModules.ForFixedUUID.class,
            MockModules.ForConfigService.class,
    })
    public interface Container {
        void inject(BulkDataSanitizerImplTest test);
    }

    @BeforeEach
    public void setup() {
        Container container = DaggerCSVFileHandlerTest_Container.create();
        container.inject(this);

        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
            .pseudonymizationSalt("salt")
            .defaultScopeId("hris")
            .pseudonymImplementation(PseudonymImplementation.LEGACY)
            .build());
    }

    @Test
    @SneakyThrows
    void handle_pseudonymize() {
        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,DEPARTMENT,EFFECTIVE_ISOWEEK\r\n" +
                "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering,2020-01-06\r\n" +
                "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",Sales,2020-01-06\r\n" +
                "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\",Engineering,2020-01-06\r\n" +
                "4,,Engineering,2020-01-06\r\n"; //blank ID

        CsvRules rules = CsvRules.builder()
            .columnToPseudonymize("EMPLOYEE_EMAIL")
            .build();

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = columnarFileSanitizerImpl.sanitize(in, rules, pseudonymizer);

            assertEquals(EXPECTED, new String(result));
        }
    }

    @Test
    @SneakyThrows
    void handle_redaction() {
        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,EFFECTIVE_ISOWEEK\r\n" +
                "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",2020-01-06\r\n" +
                "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",2020-01-06\r\n" +
                "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\",2020-01-06\r\n" +
                "4,,2020-01-06\r\n"; //blank ID

        CsvRules rules = CsvRules.builder()
            .columnToPseudonymize("EMPLOYEE_EMAIL")
            .columnToRedact("DEPARTMENT")
            .build();

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = columnarFileSanitizerImpl.sanitize(in, rules, pseudonymizer);

            assertEquals(EXPECTED, new String(result));
        }
    }

    @Test
    @SneakyThrows
    void handle_cased() {
        final String EXPECTED = "EMPLOYEE_ID,AN EMAIL,SOME DEPARTMENT\r\n" +
                "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n";


        CsvRules rules = CsvRules.builder()
                .columnToPseudonymize("EMPLOYEE_ID")
                .columnToPseudonymize("AN EMAIL").build();

        File inputFile = new File(getClass().getResource("/csv/hris-example-headers-w-spaces.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = columnarFileSanitizerImpl.sanitize(in, rules, pseudonymizer);

            assertEquals(EXPECTED, new String(result));
        }
    }

    @Test
    @SneakyThrows
    void handle_quotes() {
        final String EXPECTED = "EMPLOYEE_ID,EMAIL,DEPARTMENT\r\n" +
                "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",\",,,\"\r\n";


        CsvRules rules = CsvRules.builder()
                .columnToPseudonymize("EMPLOYEE_ID")
                .columnToPseudonymize("EMAIL")
                .build();
        File inputFile = new File(getClass().getResource("/csv/hris-example-quotes.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = columnarFileSanitizerImpl.sanitize(in, rules, pseudonymizer);

            assertEquals(EXPECTED, new String(result));
        }
    }

    @Test
    @SneakyThrows
    void defaultRules() {

        final String EXPECTED = "EMPLOYEE_ID,employee_EMAIL,MANAGER_id,Manager_Email,JOIN_DATE,ROLE\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"mfsaNYuCX__xvnRz4gJp_t0zrDTC5DkuCJvMkubugsI\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"TtDWXFAQxNE8O2w7DuMtEKzTSZXERuUVLCjmd9r6KQ4\"\"}\",2021-01-01,Accounting Manager\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"mfsaNYuCX__xvnRz4gJp_t0zrDTC5DkuCJvMkubugsI\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"TtDWXFAQxNE8O2w7DuMtEKzTSZXERuUVLCjmd9r6KQ4\"\"}\",,,2020-01-01,CEO\r\n";

        ConfigService config = mock(ConfigService.class);
        when(config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
            .thenReturn(Optional.of(Base64.encodeBase64String(TestUtils.getData("rules/hris/csv.yaml"))));

        CsvRules rules = (CsvRules) rulesUtils.getRulesFromConfig(config).orElseThrow();

        File inputFile = new File(getClass().getResource("/csv/hris-default-rules.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = columnarFileSanitizerImpl.sanitize(in, rules, pseudonymizer);

            assertEquals(EXPECTED, new String(result));
        }
    }


    @Test
    @SneakyThrows
    void validCaseInsensitiveAndTrimRules() {
        final String EXPECTED = "EMPLOYEE_ID,AN EMAIL,SOME DEPARTMENT\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n";

        CsvRules rules = CsvRules.builder()
                .columnToPseudonymize("    employee_id     ")
                .columnToPseudonymize(" an EMAIL ")
                .build();

        File inputFile = new File(getClass().getResource("/csv/hris-example-headers-w-spaces.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = columnarFileSanitizerImpl.sanitize(in, rules, pseudonymizer);

            assertEquals(EXPECTED, new String(result));
        }
    }

    @Test
    @SneakyThrows
    void handle_rename() {
        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,DEPARTMENT\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",\",,,\"\r\n";


        CsvRules rules = CsvRules.builder()
                .columnToPseudonymize("EMPLOYEE_ID")
                .columnToPseudonymize("EMPLOYEE_EMAIL")
                .columnsToRename(ImmutableMap.of("EMAIL", "EMPLOYEE_EMAIL"))
                .build();

        File inputFile = new File(getClass().getResource("/csv/hris-example-quotes.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = columnarFileSanitizerImpl.sanitize(in, rules, pseudonymizer);

            assertEquals(EXPECTED, new String(result));
        }
    }

    @Test
    @SneakyThrows
    void handle_duplicates() {
        //this is a lookup-table use case (for customers to use in own data warehouse)
        final String EXPECTED = "EMPLOYEE_ID,DEPARTMENT,EMPLOYEE_ID_ORIG\r\n" +
            "SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM,Engineering,1\r\n" +
            "mfsaNYuCX__xvnRz4gJp_t0zrDTC5DkuCJvMkubugsI,Sales,2\r\n" +
            "\".ZdDGUuOMK.Oy7_PJ3pf9SYX12.3tKPdLHfYbjVGcGk\",Engineering,3\r\n" +
            "\".fs1T64Micz8SkbILrABgEv4kSg.tFhvhP35HGSLdOo\",Engineering,4\r\n";

        CsvRules rules = CsvRules.builder()
                .pseudonymFormat(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                .columnToPseudonymize("EMPLOYEE_ID")
                .columnToRedact("EMPLOYEE_EMAIL")
                .columnToRedact("EFFECTIVE_ISOWEEK")
                .columnsToDuplicate(Map.of("EMPLOYEE_ID", "EMPLOYEE_ID_ORIG"))
                .build();

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = columnarFileSanitizerImpl.sanitize(in, rules, pseudonymizer);

            assertEquals(EXPECTED, new String(result));
        }
    }


    @SneakyThrows
    @Test
    void acmeExample() {
        final String EXPECTED = "StartDate,EndDate,Status,Progress,Finished,RecordedDate,ResponseId,LocationLatitude,LocationLongitude,Q1,Participant Email,Participant Unique Identifier,Q_DataPolicyViolations,Rating\r\n" +
            "Start Date,End Date,Response Type,Progress,Finished,Recorded Date,Response ID,Location Latitude,Location Longitude,What is the meaning of life?,\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"qejD3BfohzeqJbqCcGMI2O0T-fn_KB24RjUbb8pjXHs\"\"}\",\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"ilQfXaLbu3b3sKjKeOzt83Uy92Yy3shc_dlq1ro60cU\"\"}\",Q_DataPolicyViolations,Rating\r\n" +
            "\"{\"\"ImportId\"\":\"\"startDate\"\",\"\"timeZone\"\":\"\"America/Los_Angeles\"\"}\",\"{\"\"ImportId\"\":\"\"endDate\"\",\"\"timeZone\"\":\"\"America/Los_Angeles\"\"}\",\"{\"\"ImportId\"\":\"\"status\"\"}\",\"{\"\"ImportId\"\":\"\"progress\"\"}\",\"{\"\"ImportId\"\":\"\"finished\"\"}\",\"{\"\"ImportId\"\":\"\"recordedDate\"\",\"\"timeZone\"\":\"\"America/Los_Angeles\"\"}\",\"{\"\"ImportId\"\":\"\"_recordId\"\"}\",\"{\"\"ImportId\"\":\"\"locationLatitude\"\"}\",\"{\"\"ImportId\"\":\"\"locationLongitude\"\"}\",\"{\"\"ImportId\"\":\"\"QID1\"\"}\",\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"jw7v7rBpw41HFGKAH8Jp8yI2QlgO7ZYVerCJkco51Ic\"\"}\",\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"aM2l7o6Vm5Z1bRaYm_tjBfIolutVG-1k8s89UsME6LA\"\"}\",\"{\"\"ImportId\"\":\"\"Q_DataPolicyViolations\"\"}\",\"{\"\"ImportId\"\":\"\"Rating\"\"}\"\r\n" +
            "9/1/22 7:50,9/1/22 7:51,32,100,1,9/1/22 7:51,R_1ie8z2GwkwzKG3h,,,3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"acme.COM\"\",\"\"hash\"\":\"\"PM3Oh15cS2rBp-kjSrOCpQvYFe8Wo3qLj1o5F3fuefI\"\"}\",\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"5NL5SaQBwE6c0L1BDjHW-BtBOXQVH8RYwY0tGGw3khk\"\"}\",,5\r\n";

        CsvRules rules = CsvRules.builder()
                .columnToPseudonymize("Participant Email")
                .columnToPseudonymize("Participant Unique Identifier")
                .columnToRedact("Participant Name")
                .columnToRedact("IPAddress")
                .columnToRedact("DeviceIdentifier")
                .columnToRedact("Duration (in seconds)")
                .columnToRedact("Last Metadata Update Timestamp")
                .columnToRedact("Start Date")
                .build();

        Pseudonymizer defaultPseudonymizer =
            pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("salt")
                .defaultScopeId("hris")
                .build());

        File inputFile = new File(getClass().getResource("/csv/example_acme_20220901.csv").getFile());


        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = columnarFileSanitizerImpl.sanitize(in, rules, defaultPseudonymizer);
            assertEquals(EXPECTED, new String(result));
        }
    }

    @Test
    @SneakyThrows
    void handle_inclusion() {
        final String EXPECTED = "EMPLOYEE_ID\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\"\r\n";

        CsvRules rules = CsvRules.builder()
                .columnToPseudonymize("EMPLOYEE_ID")
                .columnsToInclude(Lists.newArrayList("EMPLOYEE_ID"))
                .build();

        File inputFile = new File(getClass().getResource("/csv/hris-example-quotes.csv").getFile());


        try (FileReader in = new FileReader(inputFile)) {
            byte[] result  = columnarFileSanitizerImpl.sanitize(in, rules, pseudonymizer);

            assertEquals(EXPECTED, new String(result));
        }

    }
}
