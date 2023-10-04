package com.avaulta.gateway.rules;

import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathTemplateUtils {


    //TODO: improve this; some special chars outside of {} are not accounted for
    final String SPECIAL_CHAR_CLASS = "[\\.\\^\\$\\<\\>\\*\\+\\[\\]\\(\\)\\+\\-\\=\\?\\!]";

    public String asRegex(String pathTemplate) {
        //NOTE: java capturing groups names limited to A-Z, a-z and 0-9, and must start with a letter

        return "^" + pathTemplate
                .replaceAll(SPECIAL_CHAR_CLASS, "\\\\$0")
                .replaceAll("\\{([A-Za-z][A-Za-z0-9]*)\\}", "(?<$1>[^/]+)") + "$";
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

}
