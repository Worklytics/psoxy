package co.worklytics.psoxy.rules;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.MultiTypeBulkDataRules;
import com.avaulta.gateway.rules.RecordRules;
import com.avaulta.gateway.rules.transforms.Transform;
import com.google.common.base.Preconditions;
import com.jayway.jsonpath.JsonPath;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class Validator {

    public void validate(@NonNull com.avaulta.gateway.rules.RuleSet rules) {
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

    public void validate(@NonNull ColumnarRules rules) {
        Preconditions.checkNotNull(rules.getColumnsToPseudonymize());
        Preconditions.checkNotNull(rules.getColumnsToRedact());

        //check for nonsensical rules
        if (!isEmpty(rules.getColumnsToRedact()) && !isEmpty(rules.getColumnsToPseudonymize()) &&
            !Collections.disjoint(rules.getColumnsToRedact(), rules.getColumnsToDuplicate().values())) {
            log.log(Level.WARNING, "Replacing columns produced via columnsToDuplicate is nonsensical");
        }
    }

    public void validate(@NonNull RecordRules rules) {
        Preconditions.checkNotNull(rules.getFormat());
    }

    public void validate(@NonNull MultiTypeBulkDataRules rules) {
        Preconditions.checkArgument(rules.getFileRules().size() > 0, "Must have at least one file rule");

        List<String> templatesNotPrefixedWithSlash = rules.getFileRules().keySet().stream()
            .filter(k -> !k.startsWith("/"))
            .collect(Collectors.toList());

        //not invalid per se, but likely to be a mistake
        if (!templatesNotPrefixedWithSlash.isEmpty()) {
            log.warning("The following path templates do not start with '/', so likely to be wrong:\n " + templatesNotPrefixedWithSlash.stream().collect(Collectors.joining("\n")));
        }


        rules.getFileRules().values().forEach(this::validate);
    }

    public void validate(@NonNull Rules2 rules) {
        rules.getEndpoints().forEach(this::validate);
    }

    void validate(@NonNull Endpoint endpoint) {
        if (StringUtils.isBlank(endpoint.getPathTemplate())) {
            if (StringUtils.isBlank(endpoint.getPathRegex())) {
                throw new Error("Endpoint must have either pathTemplate or pathRegex. pass `/` as pathTemplate if you want base path.");
            }
            Pattern.compile(endpoint.getPathRegex());

            //TODO: validate parameter names are ALL valid java capturing group identifiers
            // eg start w letter, contain only alphanumeric
        } else {
            if (!endpoint.getPathTemplate().startsWith("/")) {
                log.warning("Path template " + endpoint.getPathTemplate() + " does not start with '/'; this is likely to be a mistake");
            }
        }

        endpoint.getTransforms().forEach(this::validate);
    }

    void validate(@NonNull Transform transform) {
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
