package co.worklytics.psoxy.rules;

import com.jayway.jsonpath.JsonPath;
import lombok.NonNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Pattern;

public class Validator {


    static public void validate(@NonNull Rules1 rules1) {
        validate(rules1.getRedactions());
        validate(rules1.getPseudonymizations());
        validate(rules1.getEmailHeaderPseudonymizations());
        validate(rules1.getPseudonymizationWithOriginals());

        if(rules1.getAllowedEndpointRegexes() != null) {
            //throws PatternSyntaxExpression if doesn't compile
            rules1.getAllowedEndpointRegexes().forEach(Pattern::compile);
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


    static void validate(@Nullable List<Rules1.Rule> rules) {
        if (rules != null) {
            rules.forEach(Validator::validate);
        }
    }

    static public void validate(@NonNull Rules1.Rule rule) {
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
