package co.worklytics.test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestUtils {

    /**
     * get byte[] data for use in tests
     * <p>
     * example usage:
     * TestUtils.getData("confluence-webhook-examples/" + file + ".json")
     *
     * @param fileName
     * @return
     * @throws Error if io problems reading file
     */
    public static byte[] getData(String fileName) {
        try {
            URL url = TestUtils.class.getClassLoader().getResource(fileName);
            if (url == null) {
                throw new IllegalArgumentException("No such file: " + fileName);
            }
            return Files.readAllBytes(Paths.get(url.toURI()));
        } catch (IOException | URISyntaxException e) {
            throw new Error(e);
        }
    }
}
