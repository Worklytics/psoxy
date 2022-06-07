package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import lombok.Getter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class CalendarTests extends DirectoryTests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_CALENDAR;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/microsoft-365/outlook-cal";

    @Getter
    final String defaultScopeId = "azure-ad";

    @Getter
    final String yamlSerializationFilepath = "microsoft-365/outlook-cal";


    @ParameterizedTest
    @ValueSource(strings = {"v1.0", "beta"})
    void events(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events";

        String jsonResponse = asJson("Events_" + apiVersion + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
            "Irvin Sayers",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting", //body
            "uniqueIdValue" // location(s) uniqueID
        );

        assertPseudonymized(sanitized,
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com"
        );

    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0", "beta"})
    void calendarEvents(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendars/AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAEGAAAiIsqMbYjsT5e-T7KzowPTAAABuC35AAA=/events";

        String jsonResponse = asJson("CalendarEvents_" + apiVersion + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
            "Irvin Sayers",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting", //body
            "uniqueIdValue" // location(s) uniqueID
        );

        assertPseudonymized(sanitized,
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com"
        );

    }

    @ParameterizedTest
    @ValueSource(strings = {"beta"})
    void calendarViews(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView";

        assertUrlAllowed(endpoint);
        assertUrlWithQueryParamsAllowed(endpoint);
        assertUrlWithSubResourcesBlocked(endpoint);

        String jsonResponse = asJson("CalendarView_" + apiVersion + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
            "Irvin Sayers",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting", //body
            "uniqueIdValue" // location(s) uniqueID
        );

        assertPseudonymized(sanitized,
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com"
        );

    }


    @ParameterizedTest
    @ValueSource(strings = {"v1.0", "beta"})
    void event(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events";

        String jsonResponse = asJson("Event_" + apiVersion + ".json");

        assertNotSanitized(jsonResponse,
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com",
            "Irvin Sayers",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting", //body
            "uniqueIdValue" // location(s) uniqueID
        );

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
            "Irvin Sayers",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting", //body
            "uniqueIdValue" // location(s) uniqueID
            );

        assertPseudonymized(sanitized,
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0", "beta"})
    public void mailboxSettings(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/mailboxSettings";

        assertUrlAllowed(endpoint);
        assertUrlWithSubResourcesBlocked(endpoint);
    }
}
