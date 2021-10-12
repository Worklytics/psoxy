package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import com.jayway.jsonpath.JsonPath;

import java.util.regex.Pattern;

public class Validator {


    static public void validate(Rules rules) {
        rules.getRedactions().forEach(Validator::validate);
        rules.getPseudonymizations().forEach(Validator::validate);
        rules.getEmailHeaderPseudonymizations().forEach(Validator::validate);
    }

    static public void validate(Rules.Rule rule) {
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
