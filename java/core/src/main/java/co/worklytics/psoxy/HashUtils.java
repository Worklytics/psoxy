package co.worklytics.psoxy;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HashUtils {

    public String hash(String... fragments) {
        // No padding saves us the '=' character
        // https://www.baeldung.com/java-base64-encode-and-decode#2-java-8-base64-encoding-without-padding
        String hash = new String(
            Base64.getEncoder()
                .withoutPadding()
                .encode(DigestUtils.sha256(String.join("", fragments))),
            StandardCharsets.UTF_8);

        // To avoid urlencoding issues (especially with handlebars/template rendering)
        // while not increasing % of collisions, replace base64 non-alphanumeric characters
        // with urlencode unreserved alternatives (plus no padding from before)
        // See: https://handlebarsjs.com/guide/#html-escaping
        // https://en.wikipedia.org/wiki/Base64#Base64_table
        // https://en.wikipedia.org/wiki/Percent-encoding#Types_of_URI_characters
        return StringUtils.replaceChars(hash, "/+", "_.");
    }
}
