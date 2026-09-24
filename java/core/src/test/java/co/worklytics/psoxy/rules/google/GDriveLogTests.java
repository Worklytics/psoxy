package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import com.avaulta.gateway.rules.transforms.HashIp;
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
            "neil@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);
        assertNotSanitized(jsonString, "203.0.113.42");

        String sanitized =
            sanitizer.sanitize("GET", new URL(endpoint), jsonString);

        assertPseudonymized(sanitized, PII);
        assertTransformed(sanitized, Arrays.asList("203.0.113.42"), HashIp.builder().build());
        assertPseudonymized(sanitized,
            "117927411761819390794",
            "100531288453445237356",
            "0neiluseridxxxxx"
        );

        assertNotSanitized(jsonString,
            "Meeting notes",
            "Shared with Finance",
            "Q3 planning spreadsheet",
            "Customer list.csv",
            "FY26 compensation model",
            "Draft - compensation",
            "Copy of Meeting notes",
            "Personal drafts",
            "salary review 2024",
            "Confidential",
            "Owning team",
            "4242424242.pdf"
        );
        assertRedacted(sanitized,
            "Meeting notes",
            "Shared with Finance",
            "Q3 planning spreadsheet",
            "Customer list.csv",
            "FY26 compensation model",
            "Draft - compensation",
            "Copy of Meeting notes",
            "Personal drafts",
            "salary review 2024",
            "Confidential",
            "Owning team",
            "4242424242.pdf"
        );

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
