package co.worklytics.psoxy.rules.generics;

import com.avaulta.gateway.rules.transforms.Transform;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Calendar {

    /**
     * strings that, by convention, indicate that a calendar event is time blocked by individual for
     * focus - rather than a work event in and of itself
     */
    public static final List<String> FOCUS_TIME_BLOCK_TITLE_SNIPPETS = Arrays.asList(
        "Focus Time Block",
        "Focus Time",
        "Focus:",
        "No Meeting Block",
        "No Meetings Block",
        "No Meetings",
        "No Meeting"
    );

    public static final String PRESERVE_FOCUS_TIME_BLOCK_TITLE_SNIPPETS_PATTERN = "(?i)(" +
        FOCUS_TIME_BLOCK_TITLE_SNIPPETS.stream()
            .map(s -> Pattern.quote(s))
            .collect(Collectors.joining("|"))
        + ")";


    public static final Transform.RedactExceptSubstringsMatchingRegexes PRESERVE_FOCUS_TIME_BLOCK_TITLE_SNIPPETS =
            Transform.RedactExceptSubstringsMatchingRegexes.builder()
                .exception(PRESERVE_FOCUS_TIME_BLOCK_TITLE_SNIPPETS_PATTERN)
            .build();

}
