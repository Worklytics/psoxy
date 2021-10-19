package co.worklytics.psoxy;

import co.worklytics.test.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class RulesTest {


    final String EXAMPLE_YAML =
        "pseudonymizations:\n" +
        "- relativeUrlRegex: \"/calendar/v3/calendars/.*/events.*\"\n" +
        "  jsonPaths:\n" +
        "  - \"$..email\"\n" +
        "emailHeaderPseudonymizations: []\n" +
        "redactions:\n" +
        "- relativeUrlRegex: \"/calendar/v3/calendars/.*/events.*\"\n" +
        "  jsonPaths:\n" +
        "  - \"$..displayName\"\n" +
        "  - \"$.items[*].extendedProperties.private\"\n";

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

        assertEquals("/calendar/v3/calendars/.*/events.*", fromYaml.getPseudonymizations().get(0).getRelativeUrlRegex());
    }

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

}
