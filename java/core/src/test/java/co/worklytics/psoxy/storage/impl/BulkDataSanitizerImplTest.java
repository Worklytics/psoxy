package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
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
import com.avaulta.gateway.rules.transforms.FieldTransformPipeline;
import com.avaulta.gateway.rules.transforms.FieldTransform;
import com.avaulta.gateway.rules.transforms.Transform;
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
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.*;
import java.util.Arrays;
import java.util.List;
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
        MockModules.ForSecretStore.class,
        ForPlaceholderRules.class,
    })
    public interface Container {
        void inject(BulkDataSanitizerImplTest test);
    }

    @Module
    public interface ForPlaceholderRules {
        @Provides @Singleton
        static ColumnarRules ruleSet() {
            return MockModules.provideMock(ColumnarRules.class);
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
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\",\"\"h_4\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering,2023-01-06,,2019-11-11,\r\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\",\"\"h_4\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",Sales,2023-01-06,1,2020-01-01,\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\",\"\"h_4\"\":\"\"BlQB8Vk0VwdbdWTGAzBF-ote1357Ajr0fFcgFf72kdk\"\"}\",Engineering,2023-01-06,1,2019-10-06,2022-12-08\r\n" +
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
    void handle_pseudonymizeIfPresent() {
        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE\r\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\",\"\"h_4\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering,2023-01-06,,2019-11-11,\r\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\",\"\"h_4\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",Sales,2023-01-06,1,2020-01-01,\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\",\"\"h_4\"\":\"\"BlQB8Vk0VwdbdWTGAzBF-ote1357Ajr0fFcgFf72kdk\"\"}\",Engineering,2023-01-06,1,2019-10-06,2022-12-08\r\n" +
            "4,,Engineering,2023-01-06,1,2018-06-03,\r\n"; //blank ID

        ColumnarRules rules = ColumnarRules.builder()
            .columnToPseudonymizeIfPresent("EMPLOYEE_EMAIL")
            .columnToPseudonymizeIfPresent("EXTRA_EMAIL") //unlike 'columnToPseudonymize', this doesn't throw error
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
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\",\"\"h_4\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",2023-01-06,,2019-11-11,\r\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\",\"\"h_4\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",2023-01-06,1,2020-01-01,\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\",\"\"h_4\"\":\"\"BlQB8Vk0VwdbdWTGAzBF-ote1357Ajr0fFcgFf72kdk\"\"}\",2023-01-06,1,2019-10-06,2022-12-08\r\n" +
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
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\",\"\"h_4\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\",\"\"h_4\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n";

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
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\",\"\"h_4\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\",\"\"h_4\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",\",,,\"\r\n";        ColumnarRules rules = ColumnarRules.builder()
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
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\",\"\"h_4\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\",\"\"h_4\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"mfsaNYuCX__xvnRz4gJp_t0zrDTC5DkuCJvMkubugsI\"\",\"\"h_4\"\":\"\"-hN_i1M1DeMAicDVp6LhFgW9lH7r3_LbOpTlXYWpXVI\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"TtDWXFAQxNE8O2w7DuMtEKzTSZXERuUVLCjmd9r6KQ4\"\",\"\"h_4\"\":\"\"TtDWXFAQxNE8O2w7DuMtEKzTSZXERuUVLCjmd9r6KQ4\"\"}\",2021-01-01,Accounting Manager\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"mfsaNYuCX__xvnRz4gJp_t0zrDTC5DkuCJvMkubugsI\"\",\"\"h_4\"\":\"\"-hN_i1M1DeMAicDVp6LhFgW9lH7r3_LbOpTlXYWpXVI\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"TtDWXFAQxNE8O2w7DuMtEKzTSZXERuUVLCjmd9r6KQ4\"\",\"\"h_4\"\":\"\"TtDWXFAQxNE8O2w7DuMtEKzTSZXERuUVLCjmd9r6KQ4\"\"}\",,,2020-01-01,CEO\r\n";


        ConfigService config = MockModules.provideMock(ConfigService.class);
        when(config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
            .thenReturn(Optional.of(Base64.encodeBase64String(TestUtils.getData("sources/hris/csv.yaml"))));

        ColumnarRules rules = (ColumnarRules) rulesUtils.getRulesFromConfig(config, new EnvVarsConfigService()).orElseThrow();

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
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\",\"\"h_4\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\",\"\"h_4\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n";
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
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\",\"\"h_4\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\",\"\"h_4\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",\",,,\"\r\n";
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


    @Test
    @SneakyThrows
    void handle_duplicates_lookup_table_via_transforms() {
        Pseudonymizer defaultPseudonymizer =
            pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("salt")
                .defaultScopeId("hris")
                .build());


        //this is a lookup-table use case (for customers to use in own data warehouse)
        final String EXPECTED = "EMPLOYEE_ID,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE,EMPLOYEE_ID_TRANSFORMED\r\n" +
            "1,Engineering,2023-01-06,,2019-11-11,,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\r\n" +
            "2,Sales,2023-01-06,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc,2020-01-01,,t~-hN_i1M1DeMAicDVp6LhFgW9lH7r3_LbOpTlXYWpXVI\r\n" +
            "3,Engineering,2023-01-06,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc,2019-10-06,2022-12-08,t~4W7Sl-LI6iMzNNngivs5dLMiVw-7ob3Cyr3jn8NureY\r\n" +
            "4,Engineering,2023-01-06,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc,2018-06-03,,t~BOg00PLoiEEKyGzije3FJlKBzM6_Vjk87VJI9lTIA2o\r\n";

        ColumnarRules rules = ColumnarRules.builder()
            .pseudonymFormat(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .columnToPseudonymize("MANAGER_ID")
            .columnToRedact("EMPLOYEE_EMAIL")
            .columnToRedact("EFFECTIVE_ISOWEEK")
            .fieldsToTransform(Map.of("LOOKUP_RULE", FieldTransformPipeline.builder()
                .newName("EMPLOYEE_ID_TRANSFORMED")
                .sourceColumn("EMPLOYEE_ID")
                .transforms(Arrays.asList(
                    FieldTransform.pseudonymize(true)
                )).build()))
            .build();
        columnarFileSanitizerImpl.setRules(rules);

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, defaultPseudonymizer);
            String output = out.toString();
            assertEquals(EXPECTED, output);
        }
    }

    @Test
    @SneakyThrows
    void handle_duplicates_lookup_table_pre_0_4_48() {
        Pseudonymizer defaultPseudonymizer =
            pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymizationSalt("salt")
                .defaultScopeId("hris")
                .build());


        //this is a lookup-table use case (for customers to use in own data warehouse)
        final String EXPECTED = "EMPLOYEE_ID,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE,EMPLOYEE_ID_TRANSFORMED\r\n" +
            "1,Engineering,2023-01-06,,2019-11-11,,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\r\n" +
            "2,Sales,2023-01-06,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc,2020-01-01,,t~-hN_i1M1DeMAicDVp6LhFgW9lH7r3_LbOpTlXYWpXVI\r\n" +
            "3,Engineering,2023-01-06,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc,2019-10-06,2022-12-08,t~4W7Sl-LI6iMzNNngivs5dLMiVw-7ob3Cyr3jn8NureY\r\n" +
            "4,Engineering,2023-01-06,t~0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc,2018-06-03,,t~BOg00PLoiEEKyGzije3FJlKBzM6_Vjk87VJI9lTIA2o\r\n";

        ColumnarRules rules = ColumnarRules.builder()
            .pseudonymFormat(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
            .columnToPseudonymize("EMPLOYEE_ID_TRANSFORMED")
            .columnToPseudonymize("MANAGER_ID")
            .columnToRedact("EMPLOYEE_EMAIL")
            .columnToRedact("EFFECTIVE_ISOWEEK")
            .columnsToDuplicate(Map.of("EMPLOYEE_ID", "EMPLOYEE_ID_TRANSFORMED"))
            .build();
        columnarFileSanitizerImpl.setRules(rules);

        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, defaultPseudonymizer);
            String output = out.toString();
            assertEquals(EXPECTED, output);
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
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\",\"\"h_4\"\":\"\"0zPKqEd-CtbCLB1ZSwX6Zo7uAWUvkpfHGzv9-cuYwZc\"\"}\"\r\n";
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
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\",\"\"h_4\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",Sales,2023-01-06,1,2020-01-01,\r\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\",\"\"h_4\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering,2023-01-06,,2019-11-11,\r\n" +
            "4,,Engineering,2023-01-06,1,2018-06-03,\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\",\"\"h_4\"\":\"\"BlQB8Vk0VwdbdWTGAzBF-ote1357Ajr0fFcgFf72kdk\"\"}\",Engineering,2023-01-06,1,2019-10-06,2022-12-08\r\n"
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
            "2,bob@workltyics.co,Sales,2023-01-06,1,2020-01-01,,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"Y4s0esk6oY5kfgIH2Pvdgr0NVqpKyy7fU0IVbV01xTw\"\",\"\"h_4\"\":\"\"hgs2zOvvnp8YG1adeeZCwUmAI_BUk5CFTPF_tca6OmQ\"\"}\"\r\n" +
            "1,alice@worklytics.co,Engineering,2023-01-06,,2019-11-11,,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"kwv9cWxo7TDgrt1qCegIJv7rA84s_895L_wG_y8hYjA\"\",\"\"h_4\"\":\"\"sbIXAryuJzPz0dxRh4swzuxCY9_ZetgbAQlcrI-W30g\"\"}\"\r\n" +
            "4,,Engineering,2023-01-06,1,2018-06-03,,\r\n" +
            "3,charles@workltycis.co,Engineering,2023-01-06,1,2019-10-06,2022-12-08,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"KqWJXpC.g25eQzR80kCS3RVj4L4JNngo7vFwructvNU\"\",\"\"h_4\"\":\"\"1RaWPpeCqO4wTAc849d9KY41PEXdkHcxJ32ifrLzsjQ\"\"}\"\r\n";
        ColumnarRules rules = ColumnarRules.builder()
                .fieldsToTransform(Map.of("EMPLOYEE_EMAIL", FieldTransformPipeline.builder()
                        .newName("GITHUB_USERNAME")
                        .transforms(Arrays.asList(
                                FieldTransform.filter("(.*)@.*"),
                                FieldTransform.formatString("%s_enterprise"),
                                FieldTransform.pseudonymizeWithScope("github")
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
    void transform_complex_ghusername() {

        final String LF  = "\n";

        final String CRLF  = "\r\n";

        final String INITIAL = "EMPLOYEE_ID,EMPLOYEE_EMAIL" + LF +
            "1,alice@worklytics.co" + LF +
            "2,bob.smith@worklytics.co" + LF +
            "3,charles.dickens@worklytics.co" + LF +
            "4,";

        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,GITHUB_USERNAME" + CRLF +
            "2,bob.smith@worklytics.co,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"vm45r.JHXUgXcP6.mzVLxKX4uyFbgqTIecTPqs_ibdI\"\",\"\"h_4\"\":\"\"3tfnePQDgDoBZxb6c04tqlmpKSfGyOPsUANSLMUEuYU\"\"}\"" + CRLF +
            "1,alice@worklytics.co,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"fEFRnzBKYdSKBRoK_I6P4U58TEw79SNGl8lS4Dh4ANY\"\",\"\"h_4\"\":\"\"3YAoyBkqpKrO4rk5ISA0dZSABykOBC7pEMmkL1L0HK4\"\"}\"" + CRLF +
            "4,," + CRLF +
            "3,charles.dickens@worklytics.co,\"{\"\"scope\"\":\"\"github\"\",\"\"hash\"\":\"\"LbiVG96eyVeyac3b4CQ1KviNHVK3UQtjS6spDcySBzg\"\",\"\"h_4\"\":\"\"fTYkjBMftiSOMolXYIZISI2w__u5mJ7TZrTQtifAD-Q\"\"}\"" + CRLF;

        ColumnarRules rules = ColumnarRules.builder()
            .fieldsToTransform(Map.of("EMPLOYEE_EMAIL", FieldTransformPipeline.builder()
                .newName("GITHUB_USERNAME")
                .transforms(Arrays.asList(
                    FieldTransform.javaRegExpReplace("(.*)@.*", "$1"),
                    //FieldTransform.javaRegExpReplace("(.*)@.*" + FieldTransform.JavaRegExpReplace.SEPARATOR + "$1"),
                    FieldTransform.javaRegExpReplace("([^\\.].*)\\.([^\\.].*)","$1-$2"),
                    //FieldTransform.javaRegExpReplace("([^\\.].*)\\.([^\\.].*)" + FieldTransform.JavaRegExpReplace.SEPARATOR + "$1-$2"),
                    FieldTransform.pseudonymizeWithScope("github")
                )).build()))
            .build();
        columnarFileSanitizerImpl.setRules(rules);

        // replace shuffler implementation with one that reverses the list, so deterministic
        columnarFileSanitizerImpl.setRecordShuffleChunkSize(2);
        columnarFileSanitizerImpl.makeShuffleDeterministic();


        String resultString;
        try (StringReader in = new StringReader(INITIAL);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);

            resultString = out.toString();
            assertEquals(EXPECTED, resultString);

            PseudonymizerImpl githubPseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
                .pseudonymImplementation(PseudonymImplementation.LEGACY)
                .pseudonymizationSalt(pseudonymizer.getOptions().getPseudonymizationSalt())
                .defaultScopeId("github")
                .build());

            // plain 'usernames' hash shouldn't be there either
            assertFalse(resultString.contains(pseudonymizer.pseudonymize("alice").getHash()));
            assertFalse(resultString.contains(pseudonymizer.pseudonymize("bob-smith").getHash()));
            assertFalse(resultString.contains(pseudonymizer.pseudonymize("bob.smith").getHash()));
            assertFalse(resultString.contains(pseudonymizer.pseudonymize("charles-dickens").getHash()));
            assertFalse(resultString.contains(pseudonymizer.pseudonymize("charles.dickens").getHash()));

            try (CSVParser parser = CSVParser.parse(resultString, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
                List<CSVRecord> records = parser.getRecords();
                assertTrue(records.get(0).get("GITHUB_USERNAME").contains(githubPseudonymizer.pseudonymize("bob-smith").getHash()));
                assertTrue(records.get(1).get("GITHUB_USERNAME").contains(githubPseudonymizer.pseudonymize("alice").getHash()));
                assertTrue(StringUtils.isBlank(records.get(2).get("GITHUB_USERNAME")));
                assertTrue(records.get(3).get("GITHUB_USERNAME").contains(githubPseudonymizer.pseudonymize("charles-dickens").getHash()));
            }

        }


    }


    @SneakyThrows
    @Test
    void transform_fromYamlComplex() {

        ColumnarRules rules = yamlMapper.readValue(getClass().getResource("/rules/csv-pipeline-complex-transformations.yaml"), ColumnarRules.class);
        columnarFileSanitizerImpl.setRules(rules);

        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE,GITHUB_USERNAME,GITHUB_USERNAME_ALTERNATIVE,GITHUB_USERNAME_CLEARTEXT\r\n" +
            "\"{\"\"hash\"\":\"\"2_hashed\"\"}\",\"{\"\"hash\"\":\"\"bob.brooks@workltyics.co_hashed\"\"}\",Sales,2023-01-06,\"{\"\"hash\"\":\"\"1_hashed\"\"}\",2020-01-01,,\"{\"\"hash\"\":\"\"bob_brooks_hashed\"\"}\",\"{\"\"hash\"\":\"\"bob_brooks_alternate_hashed\"\"}\",bob_brooks\r\n" +
            "\"{\"\"hash\"\":\"\"1_hashed\"\"}\",\"{\"\"hash\"\":\"\"alice.allen@worklytics.co_hashed\"\"}\",Engineering,2023-01-06,,2019-11-11,,\"{\"\"hash\"\":\"\"alice_allen_hashed\"\"}\",\"{\"\"hash\"\":\"\"alice_allen_alternate_hashed\"\"}\",alice_allen\r\n" +
            "\"{\"\"hash\"\":\"\"4_hashed\"\"}\",\"{\"\"hash\"\":\"\"dave@worklytics.co_hashed\"\"}\",Engineering,2023-01-06,\"{\"\"hash\"\":\"\"1_hashed\"\"}\",2018-06-03,,\"{\"\"hash\"\":\"\"dave_hashed\"\"}\",\"{\"\"hash\"\":\"\"dave_alternate_hashed\"\"}\",dave\r\n" +
            "\"{\"\"hash\"\":\"\"3_hashed\"\"}\",\"{\"\"hash\"\":\"\"charles.clark@workltycis.co_hashed\"\"}\",Engineering,2023-01-06,\"{\"\"hash\"\":\"\"1_hashed\"\"}\",2019-10-06,2022-12-08,\"{\"\"hash\"\":\"\"charles_clark_hashed\"\"}\",\"{\"\"hash\"\":\"\"charles_clark_alternate_hashed\"\"}\",charles_clark\r\n";

        File inputFile = new File(getClass().getResource("/csv/hris-example-split-email-usernames.csv").getFile());

        columnarFileSanitizerImpl.setRecordShuffleChunkSize(2);
        columnarFileSanitizerImpl.makeShuffleDeterministic();

        // use stub for easy check on values
        Pseudonymizer pseudonymizer = new StubPseudonymizer();

        try (FileReader in = new FileReader(inputFile);
            StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());

            try (CSVParser parser = CSVParser.parse(out.toString(), CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
                List<CSVRecord> records = parser.getRecords();
                assertTrue(records.get(0).get("GITHUB_USERNAME").contains(pseudonymizer.pseudonymize("bob_brooks").getHash()));
                assertTrue(records.get(0).get("GITHUB_USERNAME_ALTERNATIVE").contains(pseudonymizer.pseudonymize("bob_brooks_alternate").getHash()));
                assertTrue(records.get(1).get("GITHUB_USERNAME").contains(pseudonymizer.pseudonymize("alice_allen").getHash()));
                assertTrue(records.get(1).get("GITHUB_USERNAME_ALTERNATIVE").contains(pseudonymizer.pseudonymize("alice_allen_alternate").getHash()));
                assertTrue(records.get(2).get("GITHUB_USERNAME").contains(pseudonymizer.pseudonymize("dave").getHash()));
                assertTrue(records.get(2).get("GITHUB_USERNAME_ALTERNATIVE").contains(pseudonymizer.pseudonymize("dave_alternate").getHash()));
                assertTrue(records.get(3).get("GITHUB_USERNAME").contains(pseudonymizer.pseudonymize("charles_clark").getHash()));
                assertTrue(records.get(3).get("GITHUB_USERNAME_ALTERNATIVE").contains(pseudonymizer.pseudonymize("charles_clark_alternate").getHash()));

            }
        }
    }

    @SneakyThrows
    @Test
    void transform_fromYamlWithRegExp() {

        ColumnarRules rules = yamlMapper.readValue(getClass().getResource("/rules/csv-pipeline.yaml"), ColumnarRules.class);
        columnarFileSanitizerImpl.setRules(rules);

        final String EXPECTED = "EMPLOYEE_ID,EMPLOYEE_EMAIL,DEPARTMENT,SNAPSHOT,MANAGER_ID,JOIN_DATE,LEAVE_DATE,GITHUB_USERNAME\r\n" +
            "\"{\"\"hash\"\":\"\"2_hashed\"\"}\",\"{\"\"hash\"\":\"\"bob@workltyics.co_hashed\"\"}\",Sales,2023-01-06,\"{\"\"hash\"\":\"\"1_hashed\"\"}\",2020-01-01,,\"{\"\"hash\"\":\"\"bob_acme_hashed\"\"}\"\r\n" +
            "\"{\"\"hash\"\":\"\"1_hashed\"\"}\",\"{\"\"hash\"\":\"\"alice@worklytics.co_hashed\"\"}\",Engineering,2023-01-06,,2019-11-11,,\"{\"\"hash\"\":\"\"alice_acme_hashed\"\"}\"\r\n" +
            "\"{\"\"hash\"\":\"\"4_hashed\"\"}\",,Engineering,2023-01-06,\"{\"\"hash\"\":\"\"1_hashed\"\"}\",2018-06-03,,\r\n" +
            "\"{\"\"hash\"\":\"\"3_hashed\"\"}\",\"{\"\"hash\"\":\"\"charles@workltycis.co_hashed\"\"}\",Engineering,2023-01-06,\"{\"\"hash\"\":\"\"1_hashed\"\"}\",2019-10-06,2022-12-08,\"{\"\"hash\"\":\"\"charles_acme_hashed\"\"}\"\r\n";
        File inputFile = new File(getClass().getResource("/csv/hris-example.csv").getFile());

        columnarFileSanitizerImpl.setRecordShuffleChunkSize(2);
        columnarFileSanitizerImpl.makeShuffleDeterministic();

        Pseudonymizer pseudonymizer = new StubPseudonymizer();

        try (FileReader in = new FileReader(inputFile);
             StringWriter out = new StringWriter()) {
            columnarFileSanitizerImpl.sanitize(in, out, pseudonymizer);
            assertEquals(EXPECTED, out.toString());
        }
    }


    class StubPseudonymizer implements Pseudonymizer {

        @Override
        public PseudonymizedIdentity pseudonymize(Object identifier) {
            return PseudonymizedIdentity.builder()
                .hash(identifier + "_hashed")
                .build();
        }

        @Override
        public PseudonymizedIdentity pseudonymize(Object identifier, Transform.PseudonymizationTransform transform) {
            return PseudonymizedIdentity.builder()
                .hash(identifier + "_hashed")
                .build();
        }

        @Override
        public ConfigurationOptions getOptions() {
            return ConfigurationOptions.builder().defaultScopeId("hris").build();
        }

    }
}
