package co.worklytics.psoxy.storage;

import co.worklytics.psoxy.Sanitizer;

import java.io.IOException;
import java.io.InputStreamReader;

public interface FileHandler {
    byte[] handle(InputStreamReader reader, Sanitizer sanitizer) throws IOException;
}
