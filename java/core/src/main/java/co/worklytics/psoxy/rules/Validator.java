package co.worklytics.psoxy.rules;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.MultiTypeBulkDataRules;
import com.avaulta.gateway.rules.RecordRules;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.base.Preconditions;
import com.jayway.jsonpath.JsonPath;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.N;

import java.util.Collections;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Log
public class Validator {

    static public void validate(@NonNull com.avaulta.gateway.rules.RuleSet rules) {
        if (rules instanceof ColumnarRules) {
            validate((ColumnarRules) rules);
        } else if (rules instanceof Rules2) {
            validate((Rules2) rules);
        } else if (rules instanceof MultiTypeBulkDataRules) {
            validate((MultiTypeBulkDataRules) rules);
        } else if (rules instanceof RecordRules) {
            validate((RecordRules) rules);
        } else {
          throw new NotImplementedException("Set not supported: " + rules.getClass().getSimpleName());
        }
    }

    static public void validate(@NonNull ColumnarRules rules) {
        Preconditions.checkNotNull(rules.getColumnsToPseudonymize());
        Preconditions.checkNotNull(rules.getColumnsToRedact());

        //check for nonsensical rules
        if (!isEmpty(rules.getColumnsToRedact()) && !isEmpty(rules.getColumnsToPseudonymize()) &&
            !Collections.disjoint(rules.getColumnsToRedact(), rules.getColumnsToDuplicate().values())) {
            log.log(Level.WARNING, "Replacing columns produced via columnsToDuplicate is nonsensical");
        }
    }

    static public void validate(@NonNull RecordRules rules) {
        Preconditions.checkNotNull(rules.getFormat());
    }

    static public void validate(@NonNull MultiTypeBulkDataRules rules) {
        Preconditions.checkArgument(rules.getFileRules().size() > 0, "Must have at least one file rule");

        rules.getFileRules().values().forEach(Validator::validate);
    }

    static public void validate(@NonNull Rules2 rules) {
        rules.getEndpoints().forEach(Validator::validate);
    }
    static void validate(@NonNull Endpoint endpoint) {
        if (StringUtils.isBlank(endpoint.getPathTemplate())) {
            if (StringUtils.isBlank(endpoint.getPathRegex())) {
                throw new Error("Endpoint must have either pathTemplate or pathRegex. pass `/` as pathTemplate if you want base path.");
            }
            Pattern.compile(endpoint.getPathRegex());

            //TODO: validate parameter names are ALL valid java capturing group identifiers
            // eg start w letter, contain only alphanumeric
        }

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
