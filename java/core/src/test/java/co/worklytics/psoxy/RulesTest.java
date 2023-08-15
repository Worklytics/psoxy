package co.worklytics.psoxy;

import co.worklytics.test.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class RulesTest {


    final String EXAMPLE_YAML =
        "pseudonymizations:\n" +
            "- jsonPaths:\n" +
            "  - \"$..email\"\n" +
            "  relativeUrlRegex: \"/calendar/v3/calendars/.*/events.*\"\n" +
            "redactions:\n" +
            "- jsonPaths:\n" +
            "  - \"$..displayName\"\n" +
            "  - \"$.items[*].extendedProperties.private\"\n" +
            "  relativeUrlRegex: \"/calendar/v3/calendars/.*/events.*\"\n";

    Rules rules = Rules.builder().pseudonymization(Rules.Rule.builder()
            .relativeUrlRegex("/calendar/v3/calendars/.*/events.*")
            .jsonPath("$..email")
            .build())
        .redaction(Rules.Rule.builder()
            .relativeUrlRegex("/calendar/v3/calendars/.*/events.*")
            .jsonPath("$..displayName")
            .jsonPath("$.items[*].extendedProperties.private")
            .build())
        .build();


    @SneakyThrows
    @Test
    public void yaml() {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

        assertEquals("---\n" + EXAMPLE_YAML, //q: why does it add this prefix??
            objectMapper.writeValueAsString(rules));

        Rules fromYaml = objectMapper.readerFor(Rules.class)
            .readValue(EXAMPLE_YAML);

        Pattern pattern = Pattern.compile(fromYaml.getPseudonymizations().get(0).getRelativeUrlRegex());

        assertFalse(pattern.matcher("/obviously-not-the-url").matches());
        assertTrue(pattern.matcher("/calendar/v3/calendars/primary/events/1248asdfas4532").matches());
    }

    //test from file, just to make sure no encoding weirdness causing problems with regexes
    @SneakyThrows
    @Test
    public void yaml_file() {
        byte[] data = TestUtils.getData("rules/example.yaml");

        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

        Rules fromYaml = objectMapper.readerFor(Rules.class)
            .readValue(data);

        Pattern pattern = Pattern.compile(fromYaml.getPseudonymizations().get(0).getRelativeUrlRegex());

        assertFalse(pattern.matcher("/obviously-not-the-url").matches());
        assertTrue(pattern.matcher("/calendar/v3/calendars/primary/events/1248asdfas4532").matches());

        assertEquals(Pattern.compile("/calendar/v3/calendars/.*/events.*").pattern(), pattern.pattern());
    }

    @SneakyThrows
    @Test
    public void noredactions_throws_error() {

        String yamlSurvey =
            "pseudonymizations:\n" +
            "  - csvColumns:\n" +
            "      - \"EMPLOYEE_ID\"\n";

        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        Rules fromYaml = objectMapper.readerFor(Rules.class)
            .readValue(yamlSurvey);

        assertThrows(NullPointerException.class,
            () -> fromYaml.getRedactions().stream());

    }

    @SneakyThrows
    @Test
    public void simple_survey() {

        String yamlSurvey =
            "defaultScopeIdForSource: \"hris\"\n" +
            "pseudonymizations:\n" +
                "  - csvColumns:\n" +
                "      - \"employee_id\"\n" +
                "redactions:\n" +
                "  - csvColumns:\n" +
                "      - \"employee_email\"\n"; //not expected, but just in case

        Base64.getEncoder().encode(yamlSurvey.getBytes());


        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        Rules fromYaml = objectMapper.readerFor(Rules.class)
            .readValue(yamlSurvey);

        assertEquals(1, fromYaml.getRedactions().stream().count());

        List<String> redactions = fromYaml.getRedactions().stream().map(Rules.Rule::getCsvColumns).flatMap(Collection::stream).collect(Collectors.toList());

        assertEquals(1, redactions.size());

        assertEquals("ZGVmYXVsdFNjb3BlSWRGb3JTb3VyY2U6ICJocmlzIgpwc2V1ZG9ueW1pemF0aW9uczoKICAtIGNzdkNvbHVtbnM6CiAgICAgIC0gImVtcGxveWVlX2lkIgpyZWRhY3Rpb25zOgogIC0gY3N2Q29sdW1uczoKICAgICAgLSAiZW1wbG95ZWVfZW1haWwiCg==",
            Base64.getEncoder().encodeToString(yamlSurvey.getBytes()));
    }

}
