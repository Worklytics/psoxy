package co.worklytics.psoxy;

import com.google.common.annotations.VisibleForTesting;
import lombok.NoArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * NOTE: this class is used for LEGACY pseudonymization implementation, not DEFAULT since v0.4
 *
 *
 */
@NoArgsConstructor(onConstructor_ = @Inject) // in lieu of provider
public class HashUtils {

    public String hash(String... fragments) {
        return encode(DigestUtils.sha256(String.join("", fragments)));
    }

    @VisibleForTesting
    public String encode(byte[] bytes) {
        // No padding saves us the '=' character
        // https://www.baeldung.com/java-base64-encode-and-decode#2-java-8-base64-encoding-without-padding
        String encoded = new String(
            Base64.getEncoder()
                .withoutPadding()
                .encode(bytes),
            StandardCharsets.UTF_8);

        // To avoid urlencoding issues (especially with handlebars/template rendering)
        // while not increasing % of collisions, replace base64 non-alphanumeric characters
        // with urlencode unreserved alternatives (plus no padding from before)
        // See: https://handlebarsjs.com/guide/#html-escaping
        // https://en.wikipedia.org/wiki/Base64#Base64_table
        // https://en.wikipedia.org/wiki/Percent-encoding#Types_of_URI_characters
        return StringUtils.replaceChars(encoded, "/+", "_.");
    }

    public byte[] decode(String encoded) {
        return Base64.getDecoder().decode(StringUtils.replaceChars(encoded, "_.", "/+"));
    }
}
