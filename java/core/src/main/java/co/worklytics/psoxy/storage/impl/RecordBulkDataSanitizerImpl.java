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
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Inject
    ObjectMapper objectMapper;

    RecordRules rules;

    @AssistedInject
    public RecordBulkDataSanitizerImpl(@Assisted RecordRules rules) {
        this.rules = rules;
    }



    @Override
    @Override
    public void sanitize(@NonNull co.worklytics.psoxy.gateway.StorageEventRequest request,
                         @NonNull Reader reader,
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

        RecordRules.Format format = rules.getFormat();

        if (format == RecordRules.Format.AUTO) {
            String contentType = request.getContentType();
            if (StringUtils.isBlank(contentType)) {
                log.warning("Content-Type is missing; defaulting to NDJSON for AUTO format.");
                format = RecordRules.Format.NDJSON;
            } else if (StringUtils.containsIgnoreCase(contentType, "application/json")) {
                format = RecordRules.Format.JSON_ARRAY;
            } else if (StringUtils.containsIgnoreCase(contentType, "text/csv")) {
                format = RecordRules.Format.CSV;
            } else {
                format = RecordRules.Format.NDJSON;
            }
        }

        if (format == RecordRules.Format.NDJSON) {
            sanitizeNdjson(reader, writer, compiledTransforms);
        } else if (format == RecordRules.Format.CSV) {
            sanitizeCsv(reader, writer, compiledTransforms);
        } else if (format == RecordRules.Format.JSON_ARRAY) {
            sanitizeJsonArray(reader, writer, compiledTransforms);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + format);
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
                        .get())) {
            Iterator<CSVRecord> iter = records.iterator();

            while(iter.hasNext()) {
                CSVRecord record = iter.next();
                try {
                    LinkedHashMap<String, Object> result = applyTransforms(record.toMap(), compiledTransforms);

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

    @VisibleForTesting
    void sanitizeJsonArray(@NonNull Reader reader,
                           @NonNull Writer writer,
                           @NonNull List<Triple<JsonPath, RecordTransform, MapFunction>> compiledTransforms) throws IOException {

        JsonFactory factory = objectMapper.getFactory();
        try (JsonParser parser = factory.createParser(reader)) {
            writer.write("[");
            boolean first = true;

            // Check for START_ARRAY
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalArgumentException("Input is not a JSON array");
            }

            while (parser.nextToken() != JsonToken.END_ARRAY && parser.currentToken() != null) {
                // Read the object
                // We convert to String then parse with Jayway to ensure compatibility with `applyTransforms` logic
                // that expects Jayway compatible types (Maps, etc) and to match `sanitizeNdjson` behavior.
                Object document = jsonConfiguration.jsonProvider().parse(
                    objectMapper.writeValueAsString(objectMapper.readTree(parser))
                );

                try {
                    document = applyTransforms(document, compiledTransforms);

                    if (!first) {
                        writer.write(",");
                    }
                    writer.write(jsonConfiguration.jsonProvider().toJson(document));
                    first = false;
                } catch (UnmatchedPseudonymization e) {
                    log.warning("Skipped record due to UnmatchedPseudonymization: " + e.getPath());
                }
            }
            writer.write("]");
            writer.flush();
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
    LinkedHashMap<String, Object> applyTransforms(Object document, List<Triple<JsonPath, RecordTransform, MapFunction>> compiledTransforms)
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
        return (LinkedHashMap<String, Object>) document;
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
