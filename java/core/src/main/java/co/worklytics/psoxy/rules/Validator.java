package co.worklytics.psoxy.rules;

import com.google.common.base.Preconditions;
import com.jayway.jsonpath.JsonPath;
import lombok.NonNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Pattern;

public class Validator {

    static public void validate(@NonNull RuleSet rules) {
        if (rules instanceof CsvRules) {
            validate((CsvRules) rules);
        } else if (rules instanceof Rules2) {
            validate((Rules2) rules );
        }
    }

    static public void validate(@NonNull CsvRules rules) {
        Preconditions.checkNotNull(rules.getColumnsToPseudonymize());
        Preconditions.checkNotNull(rules.getColumnsToRedact());
    }

    static public void validate(@NonNull Rules2 rules) {
        rules.getEndpoints().forEach(Validator::validate);
    }
    static void validate(@NonNull Rules2.Endpoint endpoint) {
        Pattern.compile(endpoint.getPathRegex());
        endpoint.getTransforms().forEach(Validator::validate);
    }

    static void validate(@NonNull Transform transform) {
        transform.getJsonPaths().forEach(p -> {
            try {
                JsonPath.compile(p);
            } catch (Throwable e) {
                throw new Error("JsonPath failed to compile: " + p, e);
            }
        });
    }

}
