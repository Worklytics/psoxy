package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.PseudonymizedIdentity;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.PseudonymizerImplFactory;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.Base64UrlSha256HashPseudonymEncoder;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.ColumnarRules;
import co.worklytics.psoxy.storage.BulkDataSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.collect.UnmodifiableIterator;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.*;
import lombok.extern.java.Log;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.io.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles a CSV file to apply the rules pseudonymize the content.
 * CSV should have the first row with headers and being separated with commas; content should be quoted
 * if include commas or quotes inside.
 */
@Log
public class ColumnarBulkDataSanitizerImpl implements BulkDataSanitizer {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    UrlSafeTokenPseudonymEncoder urlSafeTokenPseudonymEncoder;
    @Inject
    Base64UrlSha256HashPseudonymEncoder base64UrlSha256HashPseudonymEncoder;

    @Inject
    PseudonymizerImplFactory pseudonymizerImplFactory;

    @Getter(AccessLevel.PRIVATE)
    @Setter(onMethod_ = @VisibleForTesting)
    private int recordShuffleChunkSize = 500;

    @Setter(onMethod_ = @VisibleForTesting)
    ColumnarRules rules;

    @AssistedInject
    public ColumnarBulkDataSanitizerImpl(@Assisted ColumnarRules rules) {
        this.rules = rules;
    }

    @Override
    public void sanitize(@NonNull Reader reader,
                         @NonNull Writer writer,
                         @NonNull Pseudonymizer pseudonymizer) throws IOException {

        CSVParser records = CSVFormat
                .DEFAULT
                .withDelimiter(rules.getDelimiter())
                .withFirstRecordAsHeader()
                .withIgnoreHeaderCase()
                .withTrim()
                .parse(reader);

        Preconditions.checkArgument(records.getHeaderMap() != null, "Failed to parse header from file");

        Set<String> columnsToRedact = asSetWithCaseInsensitiveComparator(rules.getColumnsToRedact());

        Set<String> columnsToPseudonymize = asSetWithCaseInsensitiveComparator(rules.getColumnsToPseudonymize());


        Map<String, ColumnarRules.FieldTransformPipeline> columnsToTransform =
            Optional.ofNullable(rules.getFieldsToTransform()).orElse(Collections.emptyMap())
            .entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().trim().toLowerCase(),
                entry -> entry.getValue(),
                (a, b) -> a,
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));

        Optional<Set<String>> columnsToInclude =
            Optional.ofNullable(rules.getColumnsToInclude())
                .map(this::asSetWithCaseInsensitiveComparator);

        final Map<String, String> columnsToRename =
            Optional.ofNullable(rules.getColumnsToRename()).orElse(Collections.emptyMap())
            .entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().trim(),
                entry -> entry.getValue().trim(),
                (a, b) -> a,
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));

        final Map<String, String> columnsToDuplicate =
            Optional.ofNullable(rules.getColumnsToDuplicate()).orElse(Collections.emptyMap())
            .entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().trim(),
                entry -> entry.getValue().trim(),
                (a, b) -> a,
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));

        columnsToTransform.entrySet().stream()
                .forEach(entry -> columnsToDuplicate.put(entry.getKey(), entry.getValue().getNewName()));


        // headers respecting insertion order
        // when constructing the parser with ignore header case the keySet may not return values in
        // order. header map is <key, position>, order by position first, then construct the key set
        Set<String> headers = records.getHeaderMap()
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .filter(entry -> !columnsToRedact.contains(entry.getKey()))
                .filter(entry -> columnsToInclude.map(includeSet -> includeSet.contains(entry.getKey())).orElse(true))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // case-insensitive headers
        Set<String> headersCI = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        headersCI.addAll(headers);

        // check if there are columns that are configured to be pseudonymized but are not present in
        // the file
        // NOTE: used to error, but now just logs. use case is if someone is trying to be defensive
        // by pseudonymizing IF column should happen to exist
        Set<String> outputColumnsCI = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        outputColumnsCI.addAll(applyReplacements(headersCI, columnsToRename));
        Sets.SetView<String> missingColumnsToPseudonymize =
            Sets.difference(columnsToPseudonymize, outputColumnsCI);
        if (!missingColumnsToPseudonymize.isEmpty()) {
            log.info(String.format("Columns to pseudonymize (%s) missing from set found in file (%s)",
                "\"" + String.join("\",\"", missingColumnsToPseudonymize) + "\"",
                "\"" + String.join("\",\"", headersCI) + "\""));
        }


        List<String> columnNamesForOutputFile = Streams.concat(
            applyReplacements(headers, columnsToRename).stream(),
            columnsToDuplicate.values().stream())
            .collect(Collectors.toList());


        TriFunction<String, String, Pseudonymizer, String> pseudonymizationFunction = buildPseudonymizationFunction(rules);

        BiFunction<String, String, String> applyPseudonymizationIfAny = (outputColumnName, value) -> {
            if (columnsToPseudonymize.contains(outputColumnName.toLowerCase())) {
                return pseudonymizationFunction.apply(value, outputColumnName, pseudonymizer);
            }
            return value;
        };

        Map<String, Pseudonymizer> pseudonymizers = new HashMap<>();
        BiFunction<String, String, String> applyTransformIfAny = (originalColumnName, value) -> {
          if (columnsToTransform.containsKey(originalColumnName.toLowerCase())) {
              ColumnarRules.FieldTransformPipeline pipeline = columnsToTransform.get(originalColumnName.toLowerCase());

              for ( ColumnarRules.FieldValueTransform transform : pipeline.getTransforms()) {
                  if (value != null) {
                      if (transform.getFilter() != null) {
                          Pattern pattern = Pattern.compile(transform.getFilter());
                          Matcher matcher = pattern.matcher(value);
                          if (matcher.matches()) {
                              if (matcher.groupCount() > 0) {
                                  value = matcher.group(1);
                              }
                          } else {
                              value = null;
                          }
                      }

                      if (transform.getFormatString() != null) {
                          value = String.format(transform.getFormatString(), value);
                      }

                      if (transform.getPseudonymizeWithScope() != null) {
                          Pseudonymizer scopedPseudonymizer = pseudonymizer;
                          if (pseudonymizer.getOptions().getPseudonymImplementation() == PseudonymImplementation.LEGACY) {
                              scopedPseudonymizer = pseudonymizers.computeIfAbsent(transform.getPseudonymizeWithScope(),
                                  scope -> pseudonymizerImplFactory.create(pseudonymizer.getOptions().withDefaultScopeId(scope)));
                          }
                          value = pseudonymizationFunction.apply(value, pipeline.getNewName(), scopedPseudonymizer);
                      }
                  }
              }
          }
          return value;
        };

        CSVFormat csvFormat = CSVFormat.Builder.create()
            .setHeader(columnNamesForOutputFile.toArray(new String[0]))
            .build();

        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            UnmodifiableIterator<List<CSVRecord>> chunks =
                Iterators.partition(records.iterator(), this.getRecordShuffleChunkSize());

            for (UnmodifiableIterator<List<CSVRecord>> chunkIterator = chunks; chunkIterator.hasNext(); ) {
                List<CSVRecord> chunk = new ArrayList<>(chunkIterator.next());
                shuffleImplementation.apply(chunk).forEach(row -> {
                    Stream<Object> sanitized =
                        headers.stream() // only iterate on allowed columns (redaction implicit by omission)
                            .map(column -> Pair.of(columnsToRename.getOrDefault(column, column).toLowerCase(), row.get(column)))
                            .map(p -> applyPseudonymizationIfAny.apply(p.getKey(), p.getValue()));


                    //add duplicated columns, transformed/pseudonymized as necessary
                    sanitized = Streams.concat(sanitized, columnsToDuplicate.entrySet().stream()
                        .map(entry -> {
                            if (columnsToTransform.containsKey(entry.getKey().toLowerCase())) {
                                return applyTransformIfAny.apply(entry.getKey(), row.get(entry.getKey()));
                            } else {
                                return applyPseudonymizationIfAny.apply(entry.getValue(), row.get(entry.getKey()));
                            }
                        }));

                    try {
                        printer.printRecord(sanitized.collect(Collectors.toList()));
                    } catch (Throwable e) {
                        throw new RuntimeException("Failed to write row", e);
                    }
                });
            }
            writer.flush();
        }
    }

    private UnaryOperator<List<CSVRecord>> shuffleImplementation = (List<CSVRecord> l) -> {
        Collections.shuffle(l);
        return l;
    };

    @VisibleForTesting
    void makeShuffleDeterministic() {
        this.shuffleImplementation = (List<CSVRecord> l) -> {
            Collections.reverse(l);
            return l;
        };
    }

    List<String> applyReplacements(Collection<String> original, final Map<String, String> replacements) {
        return original.stream()
            .map(value -> replacements.getOrDefault(value, value))
            .collect(Collectors.toList());
    }

    Set<String> asSetWithCaseInsensitiveComparator(Collection<String> set) {
        if (set == null) {
            set = Collections.emptySet();
        }
        return set.stream()
            .map(String::trim)
            .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    }

    TriFunction<String, String, Pseudonymizer, String> buildPseudonymizationFunction(ColumnarRules rules) {
        return (value, outputColumnName, localPseudonymizer) -> {
            if (StringUtils.isBlank(value)) {
                return value;
            } else {
                try {
                    PseudonymizedIdentity identity = localPseudonymizer.pseudonymize(value);

                    if (identity == null) {
                        return null;
                    } else if (rules.getPseudonymFormat() == PseudonymEncoder.Implementations.URL_SAFE_TOKEN) {
                        if (identity.getOriginal() != null) {
                            //this shouldn't happen, bc ColumnarRules don't support preserving original
                            log.warning("Encoding pseudonym for column '" + outputColumnName + "' using format that will not include the 'original' value, althought transformation preserved it");
                        }
                        if (localPseudonymizer.getOptions().getPseudonymImplementation() == PseudonymImplementation.LEGACY) {
                            if (identity.getReversible() != null) {
                                throw new Error("Cannot encode legacy PseudonymizedIdentity with reversibles as URL_SAFE_TOKEN");
                            }
                            return urlSafeTokenPseudonymEncoder.encode(identity.fromLegacy());
                        } else {
                            return urlSafeTokenPseudonymEncoder.encode(identity.asPseudonym());
                        }
                    } else if (rules.getPseudonymFormat() == PseudonymEncoder.Implementations.URL_SAFE_HASH_ONLY) {
                        return base64UrlSha256HashPseudonymEncoder.encode(identity.asPseudonym());
                    } else {
                        //JSON
                        return objectMapper.writeValueAsString(identity);
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
