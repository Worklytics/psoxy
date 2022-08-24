package co.worklytics.psoxy.rules;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.google.common.base.Preconditions;
import com.jayway.jsonpath.JsonPath;
import lombok.NonNull;
import org.apache.commons.lang3.NotImplementedException;

import javax.annotation.Nullable;
import java.util.List;
import java.util.regex.Pattern;

public class Validator {

    static public void validate(@NonNull RuleSet rules) {
        if (rules instanceof CsvRules) {
            validate((CsvRules) rules);
        } else if (rules instanceof Rules2) {
            validate((Rules2) rules);
        } else {
          throw new NotImplementedException("Set not supported: " + rules.getClass().getSimpleName());
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

        if (transform instanceof Transform.Pseudonymize) {
            if (((Transform.Pseudonymize) transform).getEncoding() == PseudonymEncoder.Implementations.URL_SAFE_TOKEN
                && ((Transform.Pseudonymize) transform).getIncludeOriginal()) {
                throw new Error("cannot serialize output of Pseudonymize to URL_SAFE_TOKEN if including original");
            }
        }
    }

}
