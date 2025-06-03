package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Pseudonymizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;

/**
 * sanitize bulk data content according to rules
 *
 */
public interface BulkDataSanitizer {

    /**
     * Process a stream coming from a file and apply the rules through the sanitizer
     * to sanitize the content.
     *
     * @param reader        The stream reader with the source content, including header unless schema
     *                      implicit in records
     * @param writer        The stream writer to which sanitized content should be written.
     * @param pseudonymizer The pseudonymizer to use
     * @throws IOException  IO problem reading or writing
     */
    void sanitize(BufferedReader reader,
                  Writer writer,
                  Pseudonymizer pseudonymizer) throws IOException;
}
