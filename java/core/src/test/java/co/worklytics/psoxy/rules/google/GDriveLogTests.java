package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

class GDriveLogTests extends JavaRulesTestBaseCase {

    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.GDRIVE_LOG;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("google-workspace")
        .defaultScopeId("gapps")
        .sourceKind("gdrive-log")
        .build();

    @SneakyThrows
    @Test
    void activities() {
        String endpoint = "https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/drive";
        String jsonString = asJson("drive-activities.json");

        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co",
            "mary@worklytics.co",
            "bob@worklytics.co",
            "203.0.113.42"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize("GET", new URL(endpoint), jsonString);

        assertPseudonymized(sanitized, PII);

        assertNotSanitized(jsonString, "Meeting notes", "Shared with Finance");
        assertRedacted(sanitized, "Meeting notes", "Shared with Finance");

        assertUrlWithQueryParamsAllowed(endpoint);
        assertUrlBlocked("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/meet");
        assertUrlBlocked("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/chat");
        assertUrlBlocked("https://www.googleapis.com/drive/v3/files");
    }

    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/drive", "drive-activities.json")
        );
    }
}
