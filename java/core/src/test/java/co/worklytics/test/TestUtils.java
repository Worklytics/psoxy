package co.worklytics.test;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

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

    /**
     * helper if you need more test examples, or want to test compressed length of rules
     *
     * meant to be equivalent to Terraform's base64gzip function
     *
     * @see "https://developer.hashicorp.com/terraform/language/functions/base64gzip"
     */
    @SneakyThrows
    public static String asBase64Gzipped(String value) {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        GZIPOutputStream compressedStream = new GZIPOutputStream(s);
        compressedStream.write(value.getBytes(StandardCharsets.UTF_8));
        compressedStream.finish();
        compressedStream.close();

        return new String(Base64.getEncoder().encode(s.toByteArray()));
    }
}
