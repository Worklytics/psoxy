package co.worklytics.psoxy.rules.generics;

import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


// so the real behavior we want is effectively a simple classifier, rather than to redact?
// "Weekly Sprint Planning" - in theory we want to allow all of that
public class Calendar {

    /**
     * strings that, by convention, indicate that a calendar event is time blocked by individual for
     * focus - rather than a work event in and of itself
     */
    public static final List<String> FOCUS_TIME_BLOCK_SNIPPETS = Arrays.asList(
        "Focus Time",
        "Focus",
        "No Meetings",
        "No Meeting",
        "no mtg"
    );

    /**
     * strings that, by convention, indicate that a calendar event is time blocked by individual for
     * prep - rather than a work event in and of itself
     */
    public static final List<String> PREP_TIME_BLOCK_TITLE_SNIPPETS = Arrays.asList(
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


    public static final Transform.RedactExceptPhrases PRESERVE_CONVENTIONAL_PHRASE_SNIPPETS =
        Transform.RedactExceptPhrases.builder()
                .allowedPhrases(Stream.of(
                        FOCUS_TIME_BLOCK_SNIPPETS,
                        PREP_TIME_BLOCK_TITLE_SNIPPETS,
                        OOO_TITLE_SNIPPETS,
                        FORMAT_TITLE_SNIPPETS,
                        FREQUENCY_TITLE_SNIPPETS,
                        AUDIENCE_TITLE_SNIPPETS,
                        TOPICAL_TITLE_SNIPPETS)
                    .flatMap(List::stream)
                    .collect(Collectors.toList()))
            .build();

}
