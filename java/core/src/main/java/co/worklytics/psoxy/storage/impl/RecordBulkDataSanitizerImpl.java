package co.worklytics.psoxy.storage.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
import co.worklytics.psoxy.gateway.BulkModeConfig;
import co.worklytics.psoxy.gateway.StorageEventRequest;
import co.worklytics.psoxy.impl.AugmentProcessor;
import co.worklytics.psoxy.storage.BulkDataSanitizer;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.java.Log;
import org.apache.commons.lang3.ObjectUtils;

@Log
public class RecordBulkDataSanitizerImpl implements BulkDataSanitizer {


    @Getter(onMethod_ = {@VisibleForTesting})
    @Inject
    Configuration jsonConfiguration;

    @Inject
    UrlSafeTokenPseudonymEncoder encoder;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    BulkModeConfig bulkModeConfig;

    @Inject
    AugmentProcessor augmentProcessor;

    RecordRules rules;

    @AssistedInject
    public RecordBulkDataSanitizerImpl(@Assisted RecordRules rules) {
        this.rules = rules;
    }



    @Override
    public void sanitize(@NonNull StorageEventRequest request,
                         @NonNull InputStream in,
                         @NonNull OutputStream out,
                         @NonNull Pseudonymizer pseudonymizer) throws IOException {

        List<Triple<JsonPath, RecordTransform, MapFunction>> compiledTransforms =
            rules.getTransforms().stream()
                .flatMap(transform -> transform.getPaths().stream().map(path -> Triple.of(
                    JsonPath.compile(path),
                    transform,
                    getMapFunction(transform, pseudonymizer, encoder)
                )))
                .collect(Collectors.toList());

        RecordRules.Format format = rules.getFormat();

        if (format == RecordRules.Format.AUTO) {
            format = resolveAutoFormat(request);
        }

        RecordRules.Format outputFormat = bulkModeConfig.getOutputFormat()
            .orElse(format);

        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        RecordReader recordReader = createReader(format, reader, in);
        OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             RecordWriter recordWriter = createWriter(outputFormat, writer, out)) {
            
            recordWriter.beginRecordSet();
            
            Map<String, Object> record;
            while ((record = recordReader.readRecord()) != null) {
                try {
                    Map<String, Object> sanitized = sanitizeRecord(record, compiledTransforms);
                    recordWriter.writeRecord(sanitized);
                } catch (UnmatchedPseudonymization e) {
                    log.warning("Skipped record due to UnmatchedPseudonymization: " + e.getPath());
                }
            }
            
            recordWriter.endRecordSet();
        }
    }

    RecordReader createReader(RecordRules.Format format, Reader reader, InputStream in) throws IOException {
        switch (format) {
            case CSV:
                return new CsvRecordReader(reader);
            case JSON_ARRAY:
                return new JsonArrayRecordReader(reader, objectMapper, jsonConfiguration);
            case PARQUET:
                return new ParquetRecordReader(in);
            case NDJSON:
            default:
                return new NdjsonRecordReader(reader, jsonConfiguration);
        }
    }

    RecordWriter createWriter(RecordRules.Format format, Writer writer, OutputStream out) throws IOException {
        switch (format) {
            case CSV:
                return new CsvRecordWriter(writer);
            case JSON_ARRAY:
                return new JsonArrayRecordWriter(writer, objectMapper, jsonConfiguration);
            case PARQUET:
                return new ParquetRecordWriter(out);
            case NDJSON:
            default:
                return new NdjsonRecordWriter(writer, jsonConfiguration);
        }
    }

    private RecordRules.Format resolveAutoFormat(StorageEventRequest request) {
        RecordRules.Format contentTypeFormat = formatFromContentType(request.getContentType());
        if (contentTypeFormat != null) {
            return contentTypeFormat;
        }

        RecordRules.Format sourcePathFormat = formatFromSourceObjectPath(request.getSourceObjectPath());
        if (sourcePathFormat != null) {
            return sourcePathFormat;
        }

        if (StringUtils.containsIgnoreCase(request.getContentType(), "application/json")) {
            return RecordRules.Format.JSON_ARRAY;
        }

        if (StringUtils.isBlank(request.getContentType())) {
            log.warning("Content-Type is missing and file extension is not recognized; defaulting to NDJSON for AUTO format.");
        }
        return RecordRules.Format.NDJSON;
    }

    private RecordRules.Format formatFromContentType(String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return null;
        }

        if (StringUtils.containsIgnoreCase(contentType, "parquet") ||
            StringUtils.containsIgnoreCase(contentType, "application/vnd.apache.parquet")) {
            return RecordRules.Format.PARQUET;
        } else if (StringUtils.containsIgnoreCase(contentType, "text/csv") ||
            StringUtils.containsIgnoreCase(contentType, "application/csv")) {
            return RecordRules.Format.CSV;
        } else if (StringUtils.containsIgnoreCase(contentType, "ndjson") ||
            StringUtils.containsIgnoreCase(contentType, "jsonl")) {
            return RecordRules.Format.NDJSON;
        }

        return null;
    }

    private RecordRules.Format formatFromSourceObjectPath(String sourceObjectPath) {
        String path = StringUtils.lowerCase(StringUtils.defaultString(sourceObjectPath));
        if (path.endsWith(".gz")) {
            path = path.substring(0, path.length() - ".gz".length());
        }

        if (path.endsWith(".parquet")) {
            return RecordRules.Format.PARQUET;
        } else if (path.endsWith(".csv")) {
            return RecordRules.Format.CSV;
        } else if (path.endsWith(".json")) {
            return RecordRules.Format.JSON_ARRAY;
        } else if (path.endsWith(".ndjson") || path.endsWith(".jsonl")) {
            return RecordRules.Format.NDJSON;
        }

        return null;
    }


    /**
     * Apply augments then transforms to a single record.
     */
    Map<String, Object> sanitizeRecord(Map<String, Object> document,
                                       List<Triple<JsonPath, RecordTransform, MapFunction>> compiledTransforms)
            throws UnmatchedPseudonymization {
        if (ObjectUtils.isNotEmpty(rules.getAugments())) {
            augmentProcessor.applyAugments(rules.getAugments(), document);
        }
        return applyTransforms(document, compiledTransforms);
    }

    /**
     * Apply the compiled transforms to the document
     *
     * @param document JSON "document object"
     * @param compiledTransforms ordered list of compiled transforms
     * @return the transformed document
     * @throws UnmatchedPseudonymization if a pseudonymization transform should be applied, but nothing matches the path
     */
    Map<String, Object> applyTransforms(Map<String, Object> document, List<Triple<JsonPath, RecordTransform, MapFunction>> compiledTransforms)
            throws UnmatchedPseudonymization {
        for (Triple<JsonPath, RecordTransform, MapFunction> compiledTransform : compiledTransforms) {
            if (compiledTransform.getMiddle() instanceof RecordTransform.Pseudonymize) {
                Object matches = null;
                try {
                    matches = compiledTransform.getLeft().read(document, jsonConfiguration);
                } catch (JsonPathException e) {
                    // Optional paths might not exist; suppress exception and treat as no match
                    continue;
                }
                
   
                if (matches == null) {
                    // If a path evaluates to null or empty but we were expecting to pseudonymize, 
                    // we skip adding it or processing it. Note: If the path is entirely missing,
                    // we do not throw UnmatchedPseudonymization anymore because array-based transforms 
                    // contain multiple optional paths.
                    continue;
                }
            }

            try {
                compiledTransform.getLeft().map(document, compiledTransform.getRight(), jsonConfiguration);
            } catch (JsonPathException e) {
                //rule for transform doesn't match anything in document; suppress this
            }
        }

        return document;
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
