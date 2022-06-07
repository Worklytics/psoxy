package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Rules2;
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

    static public void validate(@NonNull Rules2 rules) {
        rules.getEndpoints().forEach(Validator::validate);
    }
    static void validate(@Nullable Rules2.Endpoint endpoint) {
        if (endpoint != null) {
            Pattern.compile(endpoint.getPathRegex());
            endpoint.getTransforms().forEach(Validator::validate);
        }
    }

    static void validate(@Nullable Rules2.Transform transform) {
        if (transform != null) {
            transform.getJsonPaths().forEach(p -> {
                try {
                    JsonPath.compile(p);
                } catch (Throwable e) {
                    throw new Error("JsonPath failed to compile: " + p, e);
                }
            });
        }
    }


    static void validate(@Nullable List<Rules.Rule> rules) {
        if (rules != null) {
            rules.forEach(Validator::validate);
        }
    }

    static public void validate(@NonNull Rules.Rule rule) {
        if (rule.getCsvColumns() == null || rule.getCsvColumns().isEmpty())  {
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
}
