package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.PseudonymizedIdentity;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.storage.BulkDataSanitizer;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.RecordRules;
import com.avaulta.gateway.rules.transforms.RecordTransform;
import com.google.common.annotations.VisibleForTesting;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.MapFunction;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;

@Log
public class RecordBulkDataSanitizerImpl implements BulkDataSanitizer {


    @Getter(onMethod_ = {@VisibleForTesting})
    @Inject
    Configuration jsonConfiguration;

    @Inject
    UrlSafeTokenPseudonymEncoder encoder;

    RecordRules rules;

    @AssistedInject
    public RecordBulkDataSanitizerImpl(@Assisted RecordRules rules) {
        this.rules = rules;
    }


    @Override
    public void sanitize(@NonNull Reader reader,
                         @NonNull Writer writer,
                         @NonNull Pseudonymizer pseudonymizer) throws IOException {

        if (rules.getFormat() != RecordRules.Format.NDJSON) {
            throw new IllegalArgumentException("Only NDJSON format is supported");
        }

        List<Triple<JsonPath, RecordTransform, MapFunction>> compiledTransforms =
            rules.getTransforms().stream()
            .map(transform -> Triple.of(
                JsonPath.compile(transform.getPath()),
                transform,
                getMapFunction(transform, pseudonymizer, encoder)
            ))
            .collect(Collectors.toList());


        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            while ((line = StringUtils.trimToNull(bufferedReader.readLine())) != null) {

                Object document = jsonConfiguration.jsonProvider().parse(line);


                try {
                    for (Triple<JsonPath, RecordTransform, MapFunction> compiledTransform : compiledTransforms) {
                        if (compiledTransform.getMiddle() instanceof RecordTransform.Pseudonymize) {
                            Object matches = compiledTransform.getLeft().read(document);
                            if (matches == null) {
                                throw new UnmatchedPseudonymization(compiledTransform.getMiddle().getPath());
                            }
                        }

                        try {
                            compiledTransform.getLeft().map(document, compiledTransform.getRight(), jsonConfiguration);
                        } catch (JsonPathException e) {
                            //rule for transform doesn't match anything in document; suppress this
                        }
                    }
                    writer.append(jsonConfiguration.jsonProvider().toJson(document));
                    writer.append('\n'); // NDJSON uses newlines between records
                    writer.flush(); //after each line
                } catch (UnmatchedPseudonymization e) {
                    log.warning("Skipped record due to UnmatchedPseudonymization: " + e.getPath());
                }
            }
        }
    }

    private MapFunction getMapFunction(RecordTransform transform,
                                       Pseudonymizer pseudonymizer,
                                       PseudonymEncoder encoder) {
        if (transform instanceof RecordTransform.Redact) {
            return (currentValue, configuration) -> null;
        } else if (transform instanceof RecordTransform.Pseudonymize) {
            return (currentValue, configuration) -> {
                if (currentValue == null) {
                    return null;
                } else if (currentValue instanceof String || currentValue instanceof Long || currentValue instanceof Integer) {
                    PseudonymizedIdentity pseudonymizedIdentity = pseudonymizer.pseudonymize(currentValue);

                    if (pseudonymizer.getOptions().getPseudonymImplementation() != PseudonymImplementation.DEFAULT) {
                        throw new IllegalArgumentException("Only DEFAULT (v0.4) pseudonymization is supported");
                    }

                    return encoder.encode(pseudonymizedIdentity.asPseudonym());
                } else {
                    throw new IllegalArgumentException("Pseudonymize transform only supports string/integer values");
                }
            };
        } else {
            throw new IllegalArgumentException("Unknown transform type: " + transform.getClass().getName());
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    static class UnmatchedPseudonymization extends Throwable {
        String path;
    }
}
