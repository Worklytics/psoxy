package co.worklytics.psoxy.rules.generics;

import co.worklytics.psoxy.impl.RESTApiSanitizerImpl;
import com.jayway.jsonpath.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class CalendarTest {

    Pattern pattern = Pattern.compile(
        Calendar.toCaseInsensitiveMultiPattern(Calendar.FOCUS_TIME_BLOCK_SNIPPETS));

    RESTApiSanitizerImpl restApiSanitizer = new RESTApiSanitizerImpl(null, null);


    @ValueSource(strings = {
        "Focus Time",
        "Focus Time Block",
        "No Meeting Block",
        "Focus time",
        "Focus time block",
        "No meeting block",
    })
    @ParameterizedTest
    public void focusTimeBlockTitleSnippetsExact(String input) {
        Matcher matcher = pattern.matcher(input);

        assertTrue(matcher.matches());

        String match = matcher.group();

        assertEquals(input, match);
    }

    @ValueSource(strings = {
        "Focus Talk Time",
        "Focus About Blocks",
        "No Meetings Meeting",
    })
    @ParameterizedTest
    public void focusTimeBlockTitleSnippets_noMatch(String input) {
        assertFalse(pattern.matcher(input).matches());
    }

    @CsvSource(value = {
        "Focus Time,Focus Time",
        "Secret Project Focus Time,Focus Time",
        "Focus Time Block,Focus Time Block",
        "Focus: Secret Project,Focus:",
        "No Meeting Wednesday,No Meeting",
        " No Meetings,No Meetings",
        "Prep Time,Prep Time",
        "Prep Customer Meeting,Prep "
    },
        ignoreLeadingAndTrailingWhitespace = false
    )
    @ParameterizedTest
    public void transformPreserves(String input, String expected) {
        assertEquals(expected,
            restApiSanitizer.getTransformImpl(Calendar.PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS)
                .map(input, Configuration.defaultConfiguration()));

    }
}
