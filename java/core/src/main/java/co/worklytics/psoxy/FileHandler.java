package co.worklytics.psoxy;

import java.io.IOException;
import java.io.InputStreamReader;

public interface FileHandler {
    byte[] handle(InputStreamReader reader, Sanitizer sanitizer) throws IOException;
}
