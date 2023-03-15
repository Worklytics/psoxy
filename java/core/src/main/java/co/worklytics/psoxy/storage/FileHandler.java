package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.RESTApiSanitizer;
import com.avaulta.gateway.rules.ColumnarRules;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Handle content coming from file
 */
public interface FileHandler {

    /**
     * Process a stream coming from a file and apply the rules through the sanitizer
     * to pseudonymize the content. The result after that process is returned as a byte array
     * @param reader The stream reader with the source content
     * @param columnarRules The rules to apply
     * @param pseudonymizer The pseudonymizer to use
     * @return A byte array with the processed content
     * @throws IOException
     */
    byte[] handle(InputStreamReader reader, ColumnarRules columnarRules, Pseudonymizer pseudonymizer) throws IOException;
}
