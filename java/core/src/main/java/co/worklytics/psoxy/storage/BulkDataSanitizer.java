package co.worklytics.psoxy.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.gateway.StorageEventRequest;

/**
 * sanitize bulk data content according to rules
 *
 */
public interface BulkDataSanitizer {

    /**
     * Process a stream coming from a file and apply the rules through the sanitizer
     * to sanitize the content.
     *
     * @param request       The storage event request
     * @param in            The input stream with the source content
     * @param out           The output stream to which sanitized content should be written
     * @param pseudonymizer The pseudonymizer to use
     * @throws IOException  IO problem reading or writing
     */
    void sanitize(StorageEventRequest request,
                  InputStream in,
                  OutputStream out,
                  Pseudonymizer pseudonymizer) throws IOException;
}
