package co.worklytics.psoxy.rules.generics;

import com.avaulta.gateway.rules.transforms.Transform;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Calendar {

    /**
     * strings that, by convention, indicate that a calendar event is time blocked by individual for
     * focus - rather than a work event in and of itself
     */
    public static final List<String> FOCUS_TIME_BLOCK_SNIPPETS = Arrays.asList(
        "Focus Time Block",
        "Focus Time",
        "Focus:",
        "No Meeting Block",
        "No Meetings Block",
        "No Meetings",
        "No Meeting"
    );


    public static final String toCaseInsensitiveMultiPattern(List<String> snippets) {
        return "(?i)(" +
            snippets.stream()
                .map(s -> Pattern.quote(s))
                .collect(Collectors.joining("|"))
            + ")";
    }


    /**
     * strings that, by convention, indicate that a calendar event is time blocked by individual for
     * prep - rather than a work event in and of itself
     */
    public static final List<String> PREP_TIME_BLOCK_TITLE_SNIPPETS = Arrays.asList(
        "Prep Time Block",
        "Prep Time",
        "Prep:",
        "Prep ", //avoid grabbing "Prep" just as prefix for other words
        "Prepare "
    );

    public static final List<String> OOO_TITLE_SNIPPETS = Arrays.asList(
        "OOO",
        "OOO:",
        "Out of Office",
        "Out of Office:",
        "Out of the Office",
        "Out of the Office:"
    );

    public static final Transform.RedactExceptSubstringsMatchingRegexes PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS =
            Transform.RedactExceptSubstringsMatchingRegexes.builder()
                .exception(toCaseInsensitiveMultiPattern(FOCUS_TIME_BLOCK_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(PREP_TIME_BLOCK_TITLE_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(OOO_TITLE_SNIPPETS))
            .build();

}
