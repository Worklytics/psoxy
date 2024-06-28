package co.worklytics.test;

import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUtils {

    static ObjectMapper jsonMapper = new ObjectMapper();


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


    /**
     * Utility method to print out formatted JSON for debug easily
     *
     *
     *
     *
     * @param json
     * @return
     */
    @SneakyThrows
    @SuppressWarnings("unused")
    public static String prettyPrintJson(String json) {

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
            .withoutSpacesInObjectEntries();
        printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);


        return jsonMapper
            .writer()
            .with(printer)
            .writeValueAsString(jsonMapper.readerFor(Object.class).readValue(json));

        //NOTE: Gson seems to URL-encode embedded strings!?!?!
        //  eg "64123avdfsMVA==" --> "64123avdfsMVA\u0030\0030"
        // Gson gson = new GsonBuilder().setPrettyPrinting().create();
        // return gson.toJson(JsonParser.parseString(json));
    }
    public static String prettyPrintJson(byte[] json) {
        return prettyPrintJson(new String(json));
    }



    /**
     * asserts equivalence of two strings after round-trips through Jackson, so any failure is more
     * readable than comparing non-pretty JSON, and any differences in original formatting (rather
     * than actual JSON structure/content) are ignored. eg, expected/actual can have different
     * "pretty" formatting, or one may not have "pretty" formatting at all.
     *
     * @param expected output value of test
     * @param actual output value of test
     */
    public static void assertJsonEquals(String expected, String actual) {
        assertEquals(prettyPrintJson(expected), prettyPrintJson(actual));
    }

    public static void assertNdjsonEquals(String expected, String actual) {
        Function<String, String> ndjsonToJson = (String s) -> "[" + s.replaceAll("\n", ",") + "]";
        String expectedJson = expected.replaceAll("\n", ",");

        assertEquals(expected, actual);
    }

    public static SecretKeySpec testKey() {
        return AESReversibleTokenizationStrategy.aesKeyFromPassword("secret", "salt");
    }
}