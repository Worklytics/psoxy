package com.avaulta.gateway.rules;

import lombok.Builder;
import lombok.Value;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathTemplateUtils {


    //TODO: improve this; some special chars outside of {} are not accounted for
    private static final String SPECIAL_CHAR_CLASS = "[\\.\\^\\$\\<\\>\\*\\+\\[\\]\\(\\)\\+\\-\\=\\?\\!]";

    private static final String REGEX_ALPHANUMERIC_PATH_PARAM = "\\{([A-Za-z][A-Za-z0-9]*)\\}";

    /**
     * capturing pattern that will be filled with name of Parameter captured by regex above in
     * replacement.
     *
     * resulting regex would then capture value from a path into a named group, identified by the
     * parameter name
     *
     */
    private static final String PARAM_VALUE_CAPTURING_PATTERN = "(?<$1>[^/]+)";


    /**
     * regex to match optional path parameters, eg `{foo?}`
     *
     * must be processed BEFORE special character escaping, because `?` is in the
     * {@link #SPECIAL_CHAR_CLASS} and would be escaped otherwise.
     */
    private static final Pattern OPTIONAL_PARAM_PATTERN =
        Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)\\?\\}");

    // placeholder that survives special char escaping (only uses alphanumerics + underscores)
    private static final String OPTIONAL_PLACEHOLDER_PREFIX = "__OPT_";
    private static final String OPTIONAL_PLACEHOLDER_SUFFIX = "__";

    // regex to find our placeholders after escaping and replace with actual capturing patterns
    private static final String OPTIONAL_PLACEHOLDER_REGEX =
        OPTIONAL_PLACEHOLDER_PREFIX + "([A-Za-z][A-Za-z0-9]*)" + OPTIONAL_PLACEHOLDER_SUFFIX;

    private static final String OPTIONAL_PARAM_VALUE_CAPTURING_PATTERN = "(?<$1>[^/]*)";


    public String asRegex(String pathTemplate) {
        //NOTE: java capturing groups names limited to A-Z, a-z and 0-9, and must start with a letter

        // Step 1: replace {param?} with safe placeholders BEFORE special char escaping,
        //         because `?` is in the special char class
        String withPlaceholders = OPTIONAL_PARAM_PATTERN.matcher(pathTemplate)
            .replaceAll(OPTIONAL_PLACEHOLDER_PREFIX + "$1" + OPTIONAL_PLACEHOLDER_SUFFIX);

        return "^"
            + withPlaceholders
                .replaceAll(SPECIAL_CHAR_CLASS, "\\\\$0")
                // turn `/{foo}/` into `/(?<foo>[^/]+)/`
                .replaceAll(REGEX_ALPHANUMERIC_PATH_PARAM, PARAM_VALUE_CAPTURING_PATTERN)
                // turn placeholders into optional capturing groups: `(?<foo>[^/]*)`
                .replaceAll(OPTIONAL_PLACEHOLDER_REGEX, OPTIONAL_PARAM_VALUE_CAPTURING_PATTERN)
            + "$";
    }


    public <T> Optional<T> match(Map<String, T> pathMap, String path) {
        for (Map.Entry<String, T> entry : pathMap.entrySet()) {
            Pattern p = Pattern.compile(asRegex(entry.getKey()));
            Matcher m = p.matcher(path);

            if (m.matches()) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public <T> Optional<Match<T>> matchVerbose(Map<String, T> pathMap, String path) {
        for (Map.Entry<String, T> entry : pathMap.entrySet()) {
            Pattern p = Pattern.compile(asRegex(entry.getKey()));
            Matcher m = p.matcher(path);

            if (m.matches()) {
                List<String> capturedGroups = new ArrayList<>(m.groupCount());

                //capturing groups are indexed from 1, not 0
                for (int i = 1; i <= m.groupCount(); i++) {
                    capturedGroups.add(m.group(i));
                }

                return Optional.of(Match.<T>builder()
                    .template(entry.getKey())
                    .capturedParams(capturedGroups)
                    .match(entry.getValue())
                    .build());
            }
        }
        return Optional.empty();
    }

    @Builder
    @Value
    public static class Match<T> {


        String template;

        List<String> capturedParams;

        T match;
    }

}
