package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.Base64UrlSha256HashPseudonymEncoder;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockMakers;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class BulkDataSanitizerImplTest {

    @Inject
    ColumnarBulkDataSanitizerImplFactory columnarFileSanitizerImplFactory;

    @Inject
    RESTApiSanitizerFactory sanitizerFactory;

    @Inject
    RulesUtils rulesUtils;

    @Inject
    PseudonymizerImplFactory pseudonymizerImplFactory;

    @Inject @Named("ForYAML")
    ObjectMapper yamlMapper;

    Pseudonymizer pseudonymizer;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        TestModules.ForFixedClock.class,
        TestModules.ForFixedUUID.class,
        MockModules.ForConfigService.class,
        ForPlaceholderRules.class,
    })
    public interface Container {
        void inject(BulkDataSanitizerImplTest test);
    }

    @Module
    public interface ForPlaceholderRules {
        @Provides @Singleton
        static ColumnarRules ruleSet() {
            if (MockModules.isAtLeastJava17()) {
                return mock(ColumnarRules.class, withSettings().mockMaker(MockMakers.SUBCLASS));
            } else {
                return mock(ColumnarRules.class);
            }
        }
    }


    @Inject ColumnarRules defaultRules;

    ColumnarBulkDataSanitizerImpl columnarFileSanitizerImpl;

    @BeforeEach
    public void setup() {
        Container container = DaggerBulkDataSanitizerImplTest_Container.create();
        container.inject(this);

        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
            .pseudonymizationSalt("salt")
            .defaultScopeId("hris")
            .pseudonymImplementation(PseudonymImplementation.LEGACY)
            .build());

        //make it deterministic
        columnarFileSanitizerImpl = columnarFileSanitizerImplFactory.create(defaultRules);
        columnarFileSanitizerImpl.setRecordShuffleChunkSize(1);
    }

    @Test
    @SneakyThrows
    void handle_pseudonymize() {
        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE\r\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering,2023-01-06,,2019-11-11,\r\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",Sales,2023-01-06,1,2020-01-01,\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\",Engineering,2023-01-06,1,2019-10-06,2022-12-08\r\n" +
            "4,,Engineering,2023-01-06,1,2018-06-03,\r\n"; //blank ID

        ColumnarRules rules = ColumnarRules.builder()
            .columnToPseudonymize("EMPLOYEE_EMAIL")
            .build();

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());
        columnarFileSanitizerImpl.setRules(rules);
        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }

    @Test
    @SneakyThrows
    void handle_redaction() {
        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE\r\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",2023-01-06,,2019-11-11,\r\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",2023-01-06,1,2020-01-01,\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\",2023-01-06,1,2019-10-06,2022-12-08\r\n" +
            "4,,2023-01-06,1,2018-06-03,\r\n"; //blank ID

        ColumnarRules rules = ColumnarRules.builder()
            .columnToPseudonymize("EMPLOYEE_EMAIL")
            .columnToRedact("DEPARTMENT")
            .build();

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());
        columnarFileSanitizerImpl.setRules(rules);

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }

    @Test
    @SneakyThrows
    void handle_cased() {
        final String EXPECTED = "EMPLOYEE_ID,AN EMAIL,SOME DEPARTMENT\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n";


        ColumnarRules rules = ColumnarRules.builder()
            .columnToPseudonymize("EMPLOYEE_ID")
            .columnToPseudonymize("AN EMAIL").build();

        File inputFile = new File(getClass().getResource("/csv/hris-example-headers-w-spaces.csv").getFile());
        columnarFileSanitizerImpl.setRules(rules);

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }

    @Test
    @SneakyThrows
    void handle_quotes() {
        final String EXPECTED = "EMPLOYEE_ID,EMAIL,DEPARTMENT\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",\",,,\"\r\n";

        ColumnarRules rules = ColumnarRules.builder()
            .columnToPseudonymize("EMPLOYEE_ID")
            .columnToPseudonymize("EMAIL")
            .build();
        File inputFile = new File(getClass().getResource("/csv/hris-example-quotes.csv").getFile());
        columnarFileSanitizerImpl.setRules(rules);

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
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
            .thenReturn(Optional.of(Base64.encodeBase64String(TestUtils.getData("sources/hris/csv.yaml"))));

        ColumnarRules rules = (ColumnarRules) rulesUtils.getRulesFromConfig(config).orElseThrow();

        File inputFile = new File(getClass().getResource("/csv/hris-default-rules.csv").getFile());
        columnarFileSanitizerImpl.setRules(rules);

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }


    @Test
    @SneakyThrows
    void validCaseInsensitiveAndTrimRules() {
        final String EXPECTED = "EMPLOYEE_ID,AN EMAIL,SOME DEPARTMENT\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n";

        ColumnarRules rules = ColumnarRules.builder()
            .columnToPseudonymize("    employee_id     ")
            .columnToPseudonymize(" an EMAIL ")
            .build();
        columnarFileSanitizerImpl.setRules(rules);

        File inputFile = new File(getClass().getResource("/csv/hris-example-headers-w-spaces.csv").getFile());

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }

    @Test
    @SneakyThrows
    void handle_rename() {
        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,DEPARTMENT\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",\",,,\"\r\n";


        ColumnarRules rules = ColumnarRules.builder()
            .columnToPseudonymize("EMPLOYEE_ID")
            .columnToPseudonymize("EMPLOYEE_EMAIL")
            .columnsToRename(ImmutableMap.of("EMAIL", "EMPLOYEE_EMAIL"))
            .build();
        columnarFileSanitizerImpl.setRules(rules);

        File inputFile = new File(getClass().getResource("/csv/hris-example-quotes.csv").getFile());

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }

    @Test
    @SneakyThrows
    void handle_duplicates_legacy() {
        Pseudonymizer defaultPseudonymizer =
            pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("salt")
                .defaultScopeId("hris")
                .pseudonymImplementation(PseudonymImplementation.LEGACY)
                .build());


        //this is a lookup-table use case (for customers to use in own data warehouse)
        final String EXPECTED = "EMPLOYEE_ID,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE,EMPLOYEE_ID_ORIG\r\n" +
            "t~SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM,Engineering,2023-01-06,,2019-11-11,,1\r\n" +
            "t~mfsaNYuCX__xvnRz4gJp_t0zrDTC5DkuCJvMkubugsI,Sales,2023-01-06,t~SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM,2020-01-01,,2\r\n" +
            "t~-ZdDGUuOMK-Oy7_PJ3pf9SYX12-3tKPdLHfYbjVGcGk,Engineering,2023-01-06,t~SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM,2019-10-06,2022-12-08,3\r\n" +
            "t~-fs1T64Micz8SkbILrABgEv4kSg-tFhvhP35HGSLdOo,Engineering,2023-01-06,t~SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM,2018-06-03,,4\r\n";

        ColumnarRules rules = ColumnarRules.builder()
            .pseudonymFormat(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .columnToPseudonymize("EMPLOYEE_ID")
            .columnToRedact("EMPLOYEE_EMAIL")
            .columnToPseudonymize("MANAGER_ID")
            .columnsToDuplicate(Map.of("EMPLOYEE_ID", "EMPLOYEE_ID_ORIG"))
            .build();
        columnarFileSanitizerImpl.setRules(rules);

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }


    @Test
    @SneakyThrows
    void handle_duplicates() {
        Pseudonymizer defaultPseudonymizer =
            pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("salt")
                .defaultScopeId("hris")
                .build());


        //this is a lookup-table use case (for customers to use in own data warehouse)
        final String EXPECTED = "EMPLOYEE_ID,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE,EMPLOYEE_ID_ORIG\r\n" +
            "t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc,Engineering,2023-01-06,,2019-11-11,,1\r\n" +
            "t~-hN_i1M1DeMAicDVp6LhFgW9lH7r3_LbOpTlXYWpXVI,Sales,2023-01-06,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc,2020-01-01,,2\r\n" +
            "t~4W7Sl-LI6iMzNNngivs5dLMiVw-7ob3Cyr3jn8NureY,Engineering,2023-01-06,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc,2019-10-06,2022-12-08,3\r\n" +
            "t~BOg00PLoiEEKyGzije3FJlKBzM6_Vjk87VJI9lTIA2o,Engineering,2023-01-06,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc,2018-06-03,,4\r\n";

        ColumnarRules rules = ColumnarRules.builder()
            .pseudonymFormat(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .columnToPseudonymize("EMPLOYEE_ID")
            .columnToPseudonymize("MANAGER_ID")
            .columnToRedact("EMPLOYEE_EMAIL")
            .columnToRedact("EFFECTIVE_ISOWEEK")
            .columnsToDuplicate(Map.of("EMPLOYEE_ID", "EMPLOYEE_ID_ORIG"))
            .build();
        columnarFileSanitizerImpl.setRules(rules);

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, defaultPseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }


    @SneakyThrows
    @Test
    void acmeExample() {
        final String EXPECTED = "StartDate,EndDate,Status,Progress,Finished,RecordedDate,ResponseId,LocationLatitude,LocationLongitude,Q1,Participant Email,Participant Unique Identifier,Q_DataPolicyViolations,Rating\r\n" +
            "Start Date,End Date,Response Type,Progress,Finished,Recorded Date,Response ID,Location Latitude,Location Longitude,What is the meaning of life?,\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"qejD3BfohzeqJbqCcGMI2O0T-fn_KB24RjUbb8pjXHs\"\"}\",\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"ilQfXaLbu3b3sKjKeOzt83Uy92Yy3shc_dlq1ro60cU\"\"}\",Q_DataPolicyViolations,Rating\r\n" +
            "\"{\"\"ImportId\"\":\"\"startDate\"\",\"\"timeZone\"\":\"\"America/Los_Angeles\"\"}\",\"{\"\"ImportId\"\":\"\"endDate\"\",\"\"timeZone\"\":\"\"America/Los_Angeles\"\"}\",\"{\"\"ImportId\"\":\"\"status\"\"}\",\"{\"\"ImportId\"\":\"\"progress\"\"}\",\"{\"\"ImportId\"\":\"\"finished\"\"}\",\"{\"\"ImportId\"\":\"\"recordedDate\"\",\"\"timeZone\"\":\"\"America/Los_Angeles\"\"}\",\"{\"\"ImportId\"\":\"\"_recordId\"\"}\",\"{\"\"ImportId\"\":\"\"locationLatitude\"\"}\",\"{\"\"ImportId\"\":\"\"locationLongitude\"\"}\",\"{\"\"ImportId\"\":\"\"QID1\"\"}\",\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"jw7v7rBpw41HFGKAH8Jp8yI2QlgO7ZYVerCJkco51Ic\"\"}\",\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"aM2l7o6Vm5Z1bRaYm_tjBfIolutVG-1k8s89UsME6LA\"\"}\",\"{\"\"ImportId\"\":\"\"Q_DataPolicyViolations\"\"}\",\"{\"\"ImportId\"\":\"\"Rating\"\"}\"\r\n" +
            "9/1/22 7:50,9/1/22 7:51,32,100,1,9/1/22 7:51,R_1ie8z2GwkwzKG3h,,,3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"acme.COM\"\",\"\"hash\"\":\"\"PM3Oh15cS2rBp-kjSrOCpQvYFe8Wo3qLj1o5F3fuefI\"\"}\",\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"5NL5SaQBwE6c0L1BDjHW-BtBOXQVH8RYwY0tGGw3khk\"\"}\",,5\r\n";

        ColumnarRules rules = ColumnarRules.builder()
            .columnToPseudonymize("Participant Email")
            .columnToPseudonymize("Participant Unique Identifier")
            .columnToRedact("Participant Name")
            .columnToRedact("IPAddress")
            .columnToRedact("DeviceIdentifier")
            .columnToRedact("Duration (in seconds)")
            .columnToRedact("Last Metadata Update Timestamp")
            .columnToRedact("Start Date")
            .build();

        columnarFileSanitizerImpl.setRules(rules);

        Pseudonymizer defaultPseudonymizer =
            pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("salt")
                .defaultScopeId("hris")
                .build());


        File inputFile = new File(getClass().getResource("/csv/example_acme_20220901.csv").getFile());


        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, defaultPseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }

    @Test
    @SneakyThrows
    void handle_inclusion() {
        final String EXPECTED = "EMPLOYEE_ID\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\"\r\n";

        ColumnarRules rules = ColumnarRules.builder()
            .columnToPseudonymize("EMPLOYEE_ID")
            .columnsToInclude(Lists.newArrayList("EMPLOYEE_ID"))
            .build();
        columnarFileSanitizerImpl.setRules(rules);

        File inputFile = new File(getClass().getResource("/csv/hris-example-quotes.csv").getFile());

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }

    }

    @SneakyThrows
    @Test
    void shuffle() {
        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE\r\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",Sales,2023-01-06,1,2020-01-01,\r\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering,2023-01-06,,2019-11-11,\r\n" +
            "4,,Engineering,2023-01-06,1,2018-06-03,\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\",Engineering,2023-01-06,1,2019-10-06,2022-12-08\r\n"
            ; //blank ID

        ColumnarRules rules = ColumnarRules.builder()
            .columnToPseudonymize("EMPLOYEE_EMAIL")
            .build();
        columnarFileSanitizerImpl.setRules(rules);

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());


        // replace shuffler implementation with one that reverses the list, so deterministic
        columnarFileSanitizerImpl.setRecordShuffleChunkSize(2);
        columnarFileSanitizerImpl.makeShuffleDeterministic();

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }

    @ValueSource(strings = {
        "blah",
        "blah@acme.com"
    })
    @ParameterizedTest
    void pre_v0_4_30_bulk_pseudonym_URL_SAFE_TOKEN_ENCODING(String identifier) {
        pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
            .pseudonymizationSalt("salt")
            .defaultScopeId("hris")
            .pseudonymImplementation(PseudonymImplementation.DEFAULT)
            .build());

        Base64UrlSha256HashPseudonymEncoder encoder = new Base64UrlSha256HashPseudonymEncoder();
        DeterministicTokenizationStrategy deterministicTokenizationStrategy =
            new Sha256DeterministicTokenizationStrategy("salt");

        // this is how ColumnarBulkDataSanitizerImpl.java encoded pseudonyms if pseudonym
        // format == URL_SAFE_TOKEN, and pseudonym implementation == DEFAULT
        // prior to v0.4.31:
        // see: https://github.com/Worklytics/psoxy/blob/ec0d324e0c45a6b97167b0907aa50bfdb8a45189/java/core/src/main/java/co/worklytics/psoxy/storage/impl/ColumnarBulkDataSanitizerImpl.java#L149C17-L149C17
        PseudonymizedIdentity pseudonymizedIdentity  = pseudonymizer.pseudonymize(identifier);
        String legacyEncoded = pseudonymizedIdentity.getHash();


        byte[] token = deterministicTokenizationStrategy.getToken(identifier, Function.identity());
        assertEquals(legacyEncoded,
            encoder.encode(Pseudonym.builder().hash(token).build()));

        // check that the none legacy encoding is just the legacy encoding with a "t~" prefix
        UrlSafeTokenPseudonymEncoder urlSafeTokenPseudonymEncoder = new UrlSafeTokenPseudonymEncoder();
        assertEquals("t~" + legacyEncoded + (pseudonymizedIdentity.getDomain() == null ? "" : ("@" + pseudonymizedIdentity.getDomain())),
            urlSafeTokenPseudonymEncoder.encode(pseudonymizedIdentity.asPseudonym()));
    }


    @SneakyThrows
    @Test
    void transform_ghusername() {

        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE,GITHUB_USERNAME\r\n" +
                "2,bob@workltyics.co,Sales,2023-01-06,1,2020-01-01,,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"Y4s0esk6oY5kfgIH2Pvdgr0NVqpKyy7fU0IVbV01xTw\"\"}\"\r\n" +
                "1,alice@worklytics.co,Engineering,2023-01-06,,2019-11-11,,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"kwv9cWxo7TDgrt1qCegIJv7rA84s_895L_wG_y8hYjA\"\"}\"\r\n" +
                "4,,Engineering,2023-01-06,1,2018-06-03,,\r\n" +
                "3,charles@workltycis.co,Engineering,2023-01-06,1,2019-10-06,2022-12-08,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"KqWJXpC.g25eQzR80kCS3RVj4L4JNngo7vFwructvNU\"\"}\"\r\n";

        ColumnarRules rules = ColumnarRules.builder()
                .fieldsToTransform(Map.of("EMPLOYEE_EMAIL", ColumnarRules.FieldTransformPipeline.builder()
                        .newName("GITHUB_USERNAME")
                        .transforms(Arrays.asList(
                                ColumnarRules.FieldValueTransform.filter("(.*)@.*"),
                                ColumnarRules.FieldValueTransform.formatString("%s_enterprise"),
                                ColumnarRules.FieldValueTransform.pseudonymizeWithScope("github")
                        )).build()))
                .build();
        columnarFileSanitizerImpl.setRules(rules);

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        // replace shuffler implementation with one that reverses the list, so deterministic
        columnarFileSanitizerImpl.setRecordShuffleChunkSize(2);
        columnarFileSanitizerImpl.makeShuffleDeterministic();


        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());

            String resultString = out.toString();

            assertEquals(EXPECTED, resultString);

            PseudonymizerImpl githubPseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymImplementation(PseudonymImplementation.LEGACY)
                .pseudonymizationSalt(pseudonymizer.getOptions().getPseudonymizationSalt())
                .defaultScopeId("github")
                .build());

            //validate has _enterprise appended pre-pseudonymization
            assertTrue(resultString.contains(githubPseudonymizer.pseudonymize("alice_enterprise").getHash()));
            assertFalse(resultString.contains(githubPseudonymizer.pseudonymize("alice").getHash()));

            //plain 'alice' hash shouldn't be there either
            assertFalse(resultString.contains(pseudonymizer.pseudonymize("alice").getHash()));
        }
    }


    @SneakyThrows
    @Test
    void transform_fromYaml() {

        ColumnarRules rules = yamlMapper.readValue(getClass().getResource("/rules/csv-pipeline.yaml"), ColumnarRules.class);
        columnarFileSanitizerImpl.setRules(rules);

        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE,GITHUB_USERNAME\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"mfsaNYuCX__xvnRz4gJp_t0zrDTC5DkuCJvMkubugsI\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",Sales,2023-01-06,\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",2020-01-01,,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"e_xmOtKElP3GOsE3lI1zpQWfkRPEwv1C4pKeEXsjLQk\"\"}\"\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering,2023-01-06,,2019-11-11,,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"JSDxj8fD9JR9uRsObds.ZVFDYdVRMeoF.o8uKmwzqF8\"\"}\"\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\".fs1T64Micz8SkbILrABgEv4kSg.tFhvhP35HGSLdOo\"\"}\",,Engineering,2023-01-06,\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",2018-06-03,,\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\".ZdDGUuOMK.Oy7_PJ3pf9SYX12.3tKPdLHfYbjVGcGk\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\",Engineering,2023-01-06,\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",2019-10-06,2022-12-08,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"O7GiAV8QkjmgJSeua2T1oUsggrFUr35ZPWtpFPni6mI\"\"}\"\r\n";

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        columnarFileSanitizerImpl.setRecordShuffleChunkSize(2);
        columnarFileSanitizerImpl.makeShuffleDeterministic();

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }
}
