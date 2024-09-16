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
        "Prep ",
        "Prep:",
        "Prepare "
    );

    public static final List<String> OOO_TITLE_SNIPPETS = Arrays.asList(
        "OOO",
        "Out of Office",
        "Out of the Office"
    );

    public static final List<String> EXTENDED_MEETING_TITLE_TOKENS = Arrays.asList(
        //"in",
        "check-in",
        "checkin",
        "coffee",
        "decision making",
        "decisionmaking",
        "design",
        "food",
        "fon",
        "forum",
        "game",
        "gaming",
        "hand off",
        "hand-off",
        "handoff",
        "handover",
        "hangout",
        "happy hour",
        "information sharing",
        "informationsharing",
        "leads",
        "learn",
        "lunch",
        "monthly",
        "nerd",
        "no mtg",
        "office hour",
        "onsite",
        "optional",
        "other",
        "problem solving",
        "problemsolving",
        "retro",
        "review",
        "slack",
        "social",
        "sprint",
        "stand up",
        "stand-up",
        "standup",
        "sync",
        "team building",
        "team building",
        "team meeting",
        "teambuilding",
        "trivia",
        "weekly",
        "working group",
        "working session"
    );


    public static final Transform.RedactExceptSubstringsMatchingRegexes PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS =
            Transform.RedactExceptSubstringsMatchingRegexes.builder()
                .exception(toCaseInsensitiveMultiPattern(FOCUS_TIME_BLOCK_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(PREP_TIME_BLOCK_TITLE_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(OOO_TITLE_SNIPPETS))
                .exception(toCaseInsensitiveMultiPattern(EXTENDED_MEETING_TITLE_TOKENS))
            .build();

}
