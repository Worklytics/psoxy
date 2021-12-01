package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import com.jayway.jsonpath.JsonPath;
import lombok.NonNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Pattern;

public class Validator {


    static public void validate(@NonNull Rules rules) {
        validate(rules.getRedactions());
        validate(rules.getPseudonymizations());
        validate(rules.getEmailHeaderPseudonymizations());
        validate(rules.getPseudonymizationWithOriginals());

        if(rules.getAllowedEndpointRegexes() != null) {
            //throws PatternSyntaxExpression if doesn't compile
            rules.getAllowedEndpointRegexes().forEach(Pattern::compile);
        }
    }

    static void validate(@Nullable List<Rules.Rule> rules) {
        if (rules != null) {
            rules.forEach(Validator::validate);
        }
    }

    static public void validate(@NonNull Rules.Rule rule) {
        Pattern.compile(rule.getRelativeUrlRegex());
        rule.getJsonPaths().forEach(p -> {
            try {
                JsonPath.compile(p);
            } catch (Throwable e) {
                throw new Error("JsonPath failed to compile: " + p, e);
            }
        });
    }
}
