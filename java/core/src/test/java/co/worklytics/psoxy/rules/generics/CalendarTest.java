package co.worklytics.psoxy.rules.generics;

import co.worklytics.psoxy.impl.RESTApiSanitizerImpl;
import com.jayway.jsonpath.Configuration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class CalendarTest {

    RESTApiSanitizerImpl restApiSanitizer = new RESTApiSanitizerImpl(null, null);

    @CsvSource(value = {
        "OOO,OOO",
        "OOO: Vacation,OOO",
        "OOO Conference,OOO",
        "Out of Office,Out of Office",
        "Out of the Office: Vacation,Out of the Office",
        "Focus Time,'Focus Time,Focus'",
        "Secret Project Focus Time,'Focus Time,Focus'",
        "Focus Time Block,'Focus Time,Focus'",
        "Focus: Secret Project,Focus",
        "No Meeting Wednesday,No Meeting",
        " No Meetings,No Meetings",
        "Prep Time,Prep",
        "Prep Customer Meeting,Prep",
        "Prep: Customer,Prep",

        // extended cases
        "Team weekly,weekly",
        "Team lunch,lunch",
        "Teem monthly,monthly"
    },
        ignoreLeadingAndTrailingWhitespace = false
    )
    @ParameterizedTest
    public void transformPreserves(String input, String expected) {
        assertEquals(expected,
            restApiSanitizer.getTransformImpl(Calendar.PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS)
                .map(input, Configuration.defaultConfiguration()));

    }

    @ValueSource(strings = {
      "Prepended Time",
      "prepaid planning meeting",
    })
    @ParameterizedTest
    public void transformDrops(String input) {
        assertEquals("",
            restApiSanitizer.getTransformImpl(Calendar.PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS)
                .map(input, Configuration.defaultConfiguration()));
    }

    @Test
    public void biweekly() {
        assertEquals("bi-weekly,weekly",
            restApiSanitizer.getTransformImpl(Calendar.PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS)
                .map("bi-weekly", Configuration.defaultConfiguration()));
    }

    @Disabled // do
    @Test
    public void extendedCases_self() {

        for (List<String> set : Arrays.asList(
            Calendar.FOCUS_TIME_BLOCK_SNIPPETS,
            Calendar.PREP_TIME_BLOCK_TITLE_SNIPPETS,
            Calendar.OOO_TITLE_SNIPPETS,
            Calendar.FREQUENCY_TITLE_SNIPPETS,
            Calendar.AUDIENCE_TITLE_SNIPPETS,
            Calendar.TOPICAL_TITLE_SNIPPETS
        )) {
            for (String token : set) {
                assertEquals(token,
                    restApiSanitizer.getTransformImpl(Calendar.PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS)
                        .map(token, Configuration.defaultConfiguration()));
            }
        }
    }

}
