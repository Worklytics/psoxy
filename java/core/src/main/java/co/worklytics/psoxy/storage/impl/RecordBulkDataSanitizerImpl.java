package co.worklytics.psoxy.storage.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
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
import co.worklytics.psoxy.PseudonymizedIdentity;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.storage.BulkDataSanitizer;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.java.Log;

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

        List<Triple<JsonPath, RecordTransform, MapFunction>> compiledTransforms =
            rules.getTransforms().stream()
                .map(transform -> Triple.of(
                    JsonPath.compile(transform.getPath()),
                    transform,
                    getMapFunction(transform, pseudonymizer, encoder)
                ))
                .collect(Collectors.toList());

        if (rules.getFormat() == RecordRules.Format.NDJSON) {
            sanitizeNdjson(reader, writer, compiledTransforms);
        } else if (rules.getFormat() == RecordRules.Format.CSV) {
            sanitizeCsv(reader, writer, compiledTransforms);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + rules.getFormat());
        }
    }

    @VisibleForTesting
    void sanitizeCsv(@NonNull Reader reader,
                     @NonNull Writer writer,
                     @NonNull List<Triple<JsonPath, RecordTransform, MapFunction>> compiledTransforms) throws IOException {

        try (CSVParser records = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .get()
                .parse(reader);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                        .setHeader(records.getHeaderNames().toArray(new String[0]))
                        .setRecordSeparator(records.getFirstEndOfLine()) //match source
                        .build())) {
            Iterator<CSVRecord> iter = records.iterator();

            while(iter.hasNext()) {
                CSVRecord record = iter.next();
                try {
                    LinkedHashMap result = applyTransforms(record.toMap(), compiledTransforms);

                    records.getHeaderNames()
                        .forEach(header -> {
                            try {
                                printer.print(result.get(header));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                    printer.println();
                } catch (UnmatchedPseudonymization e) {
                    log.warning("Skipped record due to UnmatchedPseudonymization: " + e.getPath());
                }
            }
        }
    }

    @VisibleForTesting
    void sanitizeNdjson(@NonNull Reader reader,
                        @NonNull Writer writer,
                        @NonNull List<Triple<JsonPath, RecordTransform, MapFunction>> compiledTransforms) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line;
            while ((line = StringUtils.trimToNull(bufferedReader.readLine())) != null) {

                Object document = jsonConfiguration.jsonProvider().parse(line);

                try {
                    document = applyTransforms(document, compiledTransforms);
                    writer.append(jsonConfiguration.jsonProvider().toJson(document));
                    writer.append('\n'); // NDJSON uses newlines between records
                    writer.flush(); //after each line
                } catch (UnmatchedPseudonymization e) {
                    log.warning("Skipped record due to UnmatchedPseudonymization: " + e.getPath());
                }
            }
        }
    }


    /**
     * Apply the compiled transforms to the document
     *
     * @param document JSON "document object"
     * @param compiledTransforms ordered list of compiled transforms
     * @return the transformed document
     * @throws UnmatchedPseudonymization if a pseudonymization transform should be applied, but nothing matches the path
     */
    LinkedHashMap applyTransforms(Object document, List<Triple<JsonPath, RecordTransform, MapFunction>> compiledTransforms)
            throws UnmatchedPseudonymization {
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

        //abusing implementation detail of the JSONPath library
        return (LinkedHashMap) document;
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
