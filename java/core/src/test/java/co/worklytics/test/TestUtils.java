package co.worklytics.test;

import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Assertions;



public class TestUtils {

    private static final int CONTEXT_LINES = 5;

    static ObjectMapper jsonMapper = new ObjectMapper();


    /**
     * get byte[] data for use in tests
     * <p>
     * example usage:
     * TestUtils.getData("confluence-webhook-examples/" + file + ".json")
     *
     * @param fileName a resource file name, relative to classpath of TestUtils.class
     * @return byte[] data from the file
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
     * returns the file content as trimmed UTF-8 string, standardizing newlines to unix-style (\n)
     *
     * avoids risk of customers cloning/forking repo with gitconfigs that convert line endings
     *
     * @param fileName to read; relative to classpath of TestUtils.class
     * @return content of the file as trimmed UTF-8 string, ensuring newlines are standardized to unix-style (\n)
     */
    public static String getDataAsUtf8UnixString(String fileName) {
        return standardizeNewlines(StringUtils.trim(new String(getData(fileName), StandardCharsets.UTF_8)));
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

        // Use Separators API (Jackson 2.9+) to put a space ONLY after the colon ("key": value),
        // matching Python json.dumps(indent=2) style used in our example .json files.
        // Jackson's default DefaultPrettyPrinter adds spaces both before AND after ("key" : value),
        // and withoutSpacesInObjectEntries() removes all spaces from around the colon ("key":"value").
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
            .withSeparators(Separators.createDefaultInstance()
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator(""));
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

    public static String standardizeNewlines(String textContent) {
        return textContent.replaceAll("\r\n", "\n")
            .replaceAll("\r", "\n");
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
        assertEqualsWithDiff(prettyPrintJson(expected), prettyPrintJson(actual));
    }

    public static void assertEqualsWithDiff(String expected, String actual) {
        assertEqualsWithDiff(expected, actual, null);
    }

    public static void assertEqualsWithDiff(String expected, String actual, String message) {
        if (Objects.equals(expected, actual)) return;

        if (expected == null || actual == null) {
            Assertions.assertEquals(expected, actual, message);
            return;
        }

        String[] expectedLines = expected.split("\\r?\\n");
        String[] actualLines = actual.split("\\r?\\n");

        if (expectedLines.length <= CONTEXT_LINES && actualLines.length <= CONTEXT_LINES) {
            Assertions.assertEquals(expected, actual, message);
        }

        int diffLine = -1;
        int minLines = Math.min(expectedLines.length, actualLines.length);
        for (int i = 0; i < minLines; i++) {
            if (!Objects.equals(expectedLines[i], actualLines[i])) {
                diffLine = i;
                break;
            }
        }
        if (diffLine == -1) {
            diffLine = minLines;
        }

        int startLine = Math.max(0, diffLine - CONTEXT_LINES);
        int endLineExpected = Math.min(expectedLines.length, diffLine + CONTEXT_LINES);
        int endLineActual = Math.min(actualLines.length, diffLine + CONTEXT_LINES);

        StringBuilder diffMsg = new StringBuilder();
        if (message != null) diffMsg.append(message).append("\n");
        diffMsg.append("Strings differ starting at line ").append(diffLine + 1).append("\n\n");

        diffMsg.append("--- EXPECTED (lines ").append(startLine + 1).append(" to ").append(endLineExpected).append(") ---\n");
        for (int i = startLine; i < endLineExpected; i++) {
            diffMsg.append(i == diffLine ? ">> " : "   ");
            diffMsg.append(String.format("%3d: ", i + 1)).append(expectedLines[i]).append("\n");
        }

        diffMsg.append("\n+++ ACTUAL (lines ").append(startLine + 1).append(" to ").append(endLineActual).append(") +++\n");
        for (int i = startLine; i < endLineActual; i++) {
            diffMsg.append(i == diffLine ? ">> " : "   ");
            diffMsg.append(String.format("%3d: ", i + 1)).append(actualLines[i]).append("\n");
        }
        diffMsg.append("\n");

        throw new org.opentest4j.AssertionFailedError(diffMsg.toString(), expected, actual);
    }

    public static void assertNdjsonEquals(String expected, String actual) {
        Function<String, String> ndjsonToJson = (String s) -> "[" + s.replaceAll("\n", ",") + "]";
        String expectedJson = expected.replaceAll("\n", ",");

        assertEqualsWithDiff(expected, actual);
    }

    public static SecretKeySpec testKey() {
        return AESReversibleTokenizationStrategy.aesKeyFromPassword("secret", "salt");
    }
}
