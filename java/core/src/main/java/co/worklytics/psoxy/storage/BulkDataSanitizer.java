package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Pseudonymizer;
import com.avaulta.gateway.rules.BulkDataRules;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * sanitize bulk data content according to rules
 *
 */
public interface BulkDataSanitizer {

    /**
     * Process a stream coming from a file and apply the rules through the sanitizer
     * to sanitize the content. The result after that process is returned as a byte array
     *
     * @param reader The stream reader with the source content, including header unless schema
     *               implicit in records
     * @param pseudonymizer The pseudonymizer to use
     * @return A byte array with the processed content
     * @throws IOException
     */
    byte[] sanitize(InputStreamReader reader,
                    Pseudonymizer pseudonymizer) throws IOException;
}
