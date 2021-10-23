package co.worklytics.psoxy;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    final String EXPECTED = "employeeId,email,department\r\n" +
        "1,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"worklytics.co\"\",\"\"hash\"\":\"\"Qf4dLJ4jfqZLn9ef4VirvYjvOnRaVI5tf5oLnM65YOA\"\"}\",Engineering\r\n" +
        "2,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltyics.co\"\",\"\"hash\"\":\"\"al4JK5KlOIsneC2DM__P_HRYe28LWYTBSf3yWKGm5yQ\"\"}\",Sales\r\n" +
        "3,\"{\"\"scope\"\":\"\"email\"\",\"\"domain\"\":\"\"workltycis.co\"\",\"\"hash\"\":\"\"BlQB8Vk0VwdbdWTGAzBF.ote1357Ajr0fFcgFf72kdk\"\"}\",Engineering\r\n";

    @Test
    void main() {

        Config config = new Config();
        config.pseudonymizationSalt = "salt";
        config.columnsToPseudonymize = Collections.singleton("email");

        File inputFile = new File(getClass().getResource("/hris-example.csv").getFile());

        StringWriter s = new StringWriter();
        Main.main(config, inputFile, s);

        assertEquals(EXPECTED, s.toString());
    }
}
