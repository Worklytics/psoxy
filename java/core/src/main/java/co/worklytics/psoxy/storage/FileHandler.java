package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Sanitizer;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Handle content coming from file
 */
public interface FileHandler {
    /**
     * Process an stream coming from a file and apply the rules through the sanitizer
     * to pseudonymize the content. The result after that process is returned as a byte array
     * @param reader The stream reader with the source content
     * @param sanitizer The sanitizer to use
     * @return A byte array with the processed content
     * @throws IOException
     */
    byte[] handle(InputStreamReader reader, Sanitizer sanitizer) throws IOException;
}
