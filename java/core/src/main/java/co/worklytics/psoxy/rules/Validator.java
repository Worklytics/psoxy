package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import com.jayway.jsonpath.JsonPath;
import lombok.NonNull;

import java.util.regex.Pattern;

public class Validator {


    static public void validate(@NonNull Rules rules) {
        rules.getRedactions().forEach(Validator::validate);
        rules.getPseudonymizations().forEach(Validator::validate);
        rules.getEmailHeaderPseudonymizations().forEach(Validator::validate);

        if(rules.getAllowedEndpointRegexes() != null) {
            //throws PatternSyntaxExpression if doesn't compile
            rules.getAllowedEndpointRegexes().forEach(Pattern::compile);
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
