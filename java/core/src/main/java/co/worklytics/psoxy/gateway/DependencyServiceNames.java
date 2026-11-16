package co.worklytics.psoxy.gateway;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.NoArgsConstructor;

/**
 * Maps a known cloud API host, or the token {@code jwks}, out of client error text.
 *
 * <p>Anything that is not one of those names is dropped. Stateless and safe to call from any thread.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class DependencyServiceNames {

    private static final Pattern API_HOST = Pattern.compile(
            "(?i)(?<![a-z0-9.-])((?:[a-z0-9-]+\\.)+(?:amazonaws|googleapis)\\.com)");

    private static final Pattern JWKS = Pattern.compile("(?i)(?<![a-z0-9])jwks(?![a-z0-9])");

    /**
     * @return a service token found in {@code text}, or empty when none of the known hosts appear
     */
    public Optional<String> findIn(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        Matcher hosts = API_HOST.matcher(text);
        while (hosts.find()) {
            Optional<String> token = tokenForHost(hosts.group(1).toLowerCase());
            if (token.isPresent()) {
                return token;
            }
        }
        if (JWKS.matcher(text).find()) {
            return Optional.of("jwks");
        }
        return Optional.empty();
    }

    private static Optional<String> tokenForHost(String host) {
        if (host.startsWith("ssm.")) {
            return Optional.of("ssm");
        }
        if (host.startsWith("secretsmanager.")) {
            return Optional.of("secretsmanager");
        }
        if (host.equals("secretmanager.googleapis.com") || host.startsWith("secretmanager.")) {
            return Optional.of("secretmanager");
        }
        if (host.startsWith("kms.") || host.startsWith("cloudkms.")) {
            return Optional.of("kms");
        }
        if (host.startsWith("jwks.")) {
            return Optional.of("jwks");
        }
        if (host.startsWith("s3.") || host.startsWith("s3-") || host.contains(".s3.")) {
            return Optional.of("s3");
        }
        if (host.startsWith("logs.")) {
            return Optional.of("logs");
        }
        if (host.startsWith("sts.")) {
            return Optional.of("sts");
        }
        if (host.equals("storage.googleapis.com")) {
            return Optional.of("gcs");
        }
        if (host.equals("pubsub.googleapis.com")) {
            return Optional.of("pubsub");
        }
        return Optional.empty();
    }
}
