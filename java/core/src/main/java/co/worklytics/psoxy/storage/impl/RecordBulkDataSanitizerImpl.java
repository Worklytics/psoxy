package co.worklytics.psoxy.storage.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.RecordRules;
import com.avaulta.gateway.rules.transforms.RecordTransform;
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

        try (RecordReader recordReader = createReader(format, reader);
             RecordWriter recordWriter = createWriter(format, writer)) {
            
            recordWriter.beginRecordSet();
            
            Object record;
            while ((record = recordReader.readRecord()) != null) {
                try {
                    Object sanitized = applyTransforms(record, compiledTransforms);
                    recordWriter.writeRecord(sanitized);
                } catch (UnmatchedPseudonymization e) {
                    log.warning("Skipped record due to UnmatchedPseudonymization: " + e.getPath());
                }
            }
            
            recordWriter.endRecordSet();
        }
    }

    RecordReader createReader(RecordRules.Format format, Reader reader) {
        switch (format) {
            case CSV:
                return new CsvRecordReader(reader);
            case JSON_ARRAY:
                return new JsonArrayRecordReader(reader, objectMapper, jsonConfiguration);
            case NDJSON:
            default:
                return new NdjsonRecordReader(reader, jsonConfiguration);
        }
    }

    RecordWriter createWriter(RecordRules.Format format, Writer writer) {
        switch (format) {
            case CSV:
                return new CsvRecordWriter(writer);
            case JSON_ARRAY:
                return new JsonArrayRecordWriter(writer, objectMapper, jsonConfiguration);
            case NDJSON:
            default:
                return new NdjsonRecordWriter(writer, jsonConfiguration);
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
