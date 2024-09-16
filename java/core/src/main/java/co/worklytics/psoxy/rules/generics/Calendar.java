package co.worklytics.psoxy.rules.generics;

import com.avaulta.gateway.rules.transforms.Transform;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


// so the real behavior we want is effectively a simple classifier, rather than to redact?
// "Weekly Sprint Planning" - in theory we want to allow all of that
public class Calendar {

    /**
     * strings that, by convention, indicate that a calendar event is time blocked by individual for
     * focus - rather than a work event in and of itself
     */
    public static final List<String> FOCUS_TIME_BLOCK_SNIPPETS = Arrays.asList(
        "Focus Time Block",
        "Focus Time",
        "Focus",
        "No Meeting Block",
        "No Meetings Block",
        "No Meetings",
        "No Meeting",
        "no mtg"
    );

    public static final String toCaseInsensitiveMultiPattern(List<String> snippets) {
        return "(?i)\\b(" +
            snippets.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length())) // longest first
                .map(s -> Pattern.quote(s))
                .collect(Collectors.joining("|"))
            + ")[\\s:]*\\b";
    }

    /**
     * strings that, by convention, indicate that a calendar event is time blocked by individual for
     * prep - rather than a work event in and of itself
     */
    public static final List<String> PREP_TIME_BLOCK_TITLE_SNIPPETS = Arrays.asList(
        "Prep Time Block",
        "Prep Time",
        "Prep"
    );

    public static final List<String> OOO_TITLE_SNIPPETS = Arrays.asList(
        "OOO",
        "Out of Office",
        "Out of the Office"
    );

    public static final List<String> FREQUENCY_TITLE_SNIPPETS = Arrays.asList(
        "daily",
        "bi-weekly",
        "biweekly",
        "weekly",
        "monthly",
        "quarterly"
     );

    public static final List<String> AUDIENCE_TITLE_SNIPPETS = Arrays.asList(
        "1:1",
        "1-on-1",
        "one-on-one",
        "all-hands",
        "all hands",
        "allhands",
        "team meeting"
    );

    public static final List<String> FORMAT_TITLE_SNIPPETS = Arrays.asList(
        "call",
        "brainstorm",
        "brainstorming",
        "brain storm",
        "check in",
        "check-in",
        "checkin",
        "coffee",
        "food",
        "happy hour",
        "lunch",
        "office hour",
        "onsite",
        "on-site",
        "social",
        "stand up",
        "stand-up",
        "standup",
        "team building",
        "teambuilding"
        );


    public static final List<String> TOPICAL_TITLE_SNIPPETS = Arrays.asList(
        "decision making",
        "decision",
        "hand off",
        "hand-off",
        "handoff",
        "handover",
        "information sharing",
        // "optional", //q: best place for this??
        "problem solving",
        "retro",
        "review",
        "sprint"
    );

    public static final Transform.RedactExceptSubstringsMatchingRegexes PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS =
            Transform.RedactExceptSubstringsMatchingRegexes.builder()
                .exception(toCaseInsensitiveMultiPattern(FOCUS_TIME_BLOCK_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(PREP_TIME_BLOCK_TITLE_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(OOO_TITLE_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(FORMAT_TITLE_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(FREQUENCY_TITLE_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(AUDIENCE_TITLE_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(TOPICAL_TITLE_SNIPPETS))
            .build();

}
