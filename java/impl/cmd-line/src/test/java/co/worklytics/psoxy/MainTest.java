package co.worklytics.psoxy;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileHandlerTest {


    @Test
    void main() {
        final String EXPECTED = "employeeId,email,department\r\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",Sales\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\",Engineering\r\n" +
            "4,,Engineering\r\n"; //blank ID


        Config config = new Config();
        config.pseudonymizationSalt = "salt";
        config.columnsToPseudonymize = Collections.singleton("email");

        File inputFile = new File(getClass().getResource("/hris-example.csv").getFile());

        StringWriter s = new StringWriter();
        FileHandler fileHandler = DaggerMain_Container.create().fileHandler();

        fileHandler.pseudonymize(config, inputFile, s);


        assertEquals(EXPECTED, s.toString());
    }


    @Test
    void main_redaction() {
        final String EXPECTED = "employeeId,email\r\n" +
            "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\"\r\n" +
            "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\"\r\n" +
            "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\"\r\n" +
            "4,\r\n"; //blank ID

        Config config = new Config();
        config.pseudonymizationSalt = "salt";
        config.columnsToRedact = Collections.singleton("department");
        config.columnsToPseudonymize = Collections.singleton("email");

        File inputFile = new File(getClass().getResource("/hris-example.csv").getFile());

        StringWriter s = new StringWriter();
        FileHandler fileHandler = DaggerMain_Container.create().fileHandler();


        fileHandler.pseudonymize(config, inputFile, s);

        assertEquals(EXPECTED, s.toString());
    }



    @Test
    void main_cased() {
        final String EXPECTED = "Employee Id,Email,Some Department\r\n" +
            "\"{\"\"scope\"\":\"\"hris\"\",\"\"hash\"\":\"\"SappwO4KZKGprqqUNruNreBD2BVR98nEM6NRCu3R2dM\"\"}\",\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n";

        Config config = new Config();
        config.pseudonymizationSalt = "salt";
        config.columnsToPseudonymize = Set.of("Employee Id","Email");
        config.defaultScopeId = "hris";

        File inputFile = new File(getClass().getResource("/hris-example-headers-w-spaces.csv").getFile());

        StringWriter s = new StringWriter();
        FileHandler fileHandler = DaggerMain_Container.create().fileHandler();
        fileHandler.pseudonymize(config, inputFile, s);

        assertEquals(EXPECTED, s.toString());
    }

}
