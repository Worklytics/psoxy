package co.worklytics.psoxy.utils;

import java.util.regex.Pattern;

/**
 * Redacts patterns that commonly correspond to PII (email addresses; UUIDs/GUIDs, which some
 * source APIs use as user ids) from free-text content before it's written to logs.
 *
 * Intended for content whose structure/schema isn't known ahead of time - eg, error response
 * bodies from source APIs - so schema-based sanitization (see RESTApiSanitizer) isn't
 * applicable. This is a best-effort text scrub, NOT a substitute for schema-based sanitization.
 */
public class LogSanitizationUtils {

    static final Pattern EMAIL_PATTERN =
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    static final Pattern UUID_PATTERN =
        Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    public static String redactPotentialPii(String content) {
        if (content == null) {
            return null;
        }
        String redacted = EMAIL_PATTERN.matcher(content).replaceAll("{redacted-email}");
        redacted = UUID_PATTERN.matcher(redacted).replaceAll("{redacted-uuid}");
        return redacted;
    }
}
