package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.PseudonymizedIdentity;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.PseudonymizerImplFactory;
import co.worklytics.psoxy.storage.BulkDataSanitizer;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.Base64UrlSha256HashPseudonymEncoder;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.transforms.FieldTransform;
import com.avaulta.gateway.rules.transforms.FieldTransformPipeline;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.java.Log;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

        /*
         * Table to store the transformation to be applied to each column
         * K = new column name
         * V = Pair<String, List<F(String)>> = original column + all functions to apply to its value
         * Note, can't use Guava Table as order of the transformations to apply is not deterministic.
         * Using LinkedHashMap will use insertion order
         */
        Map<String, Pair<String, List<Function<String, Optional<String>>>>> columnTransforms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // just for make the code more readable, consumer that fills the table
        TriConsumer<String, String, Function<String, Optional<String>>> addColumnTransform = (newColumn, sourceColumn, transform) ->
            columnTransforms.computeIfAbsent(newColumn, (s) -> Pair.of(sourceColumn, new ArrayList<>())).getValue().add(transform);

        Set<String> columnsToRedact = asSetWithCaseInsensitiveComparator(rules.getColumnsToRedact());

        Set<String> columnsToPseudonymize =
            asSetWithCaseInsensitiveComparator(rules.getColumnsToPseudonymize());

        Set<String> columnsToPseudonymizeIfPresent =
            asSetWithCaseInsensitiveComparator(rules.getColumnsToPseudonymizeIfPresent());


        Map<String, List<FieldTransformPipeline>> columnsToTransform =
            Optional.ofNullable(rules.getFieldsToTransform()).orElse(Collections.emptyMap())
            .entrySet().stream()
            .collect(Collectors.toMap(
                entry -> ObjectUtils.firstNonNull(entry.getValue().getSourceColumn(), entry.getKey()).trim().toLowerCase(),
                entry -> Lists.newArrayList(entry.getValue()),
                (a, b) -> {
                    a.addAll(b);
                    return a;
                },
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

        Set<String> newColumnNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        final Map<String, String> columnsToDuplicate =
            Optional.ofNullable(rules.getColumnsToDuplicate()).orElse(Collections.emptyMap())
            .entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().trim(),
                entry -> entry.getValue().trim(),
                (a, b) -> a,
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));

        newColumnNames.addAll(columnsToDuplicate.values());

        columnsToTransform.entrySet()
                .stream()
                .forEach(entry -> {
                    entry.getValue().stream().forEach(pipeline -> {
                        if (pipeline.getNewName() == null) {
                            throw new IllegalArgumentException("FieldTransformPipeline must have a newName");
                        }
                        newColumnNames.add(pipeline.getNewName().trim());
                    });
                });


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

        TriFunction<String, String, Pseudonymizer, String> pseudonymizationFunction = buildPseudonymizationFunction(rules);

        Map<String, Pseudonymizer> pseudonymizers = new HashMap<>();
        BiFunction<String, FieldTransformPipeline, String> applyTransform = (originalValue, pipeline) -> {
          String value = originalValue;
          for ( FieldTransform transform : pipeline.getTransforms()) {

              if (value != null) {
                  if (transform instanceof FieldTransform.Filter) {
                      Matcher matcher = ((FieldTransform.Filter) transform).getCompiledPattern().matcher(value);
                      if (matcher.matches()) {
                          if (matcher.groupCount() > 0) {
                              value = matcher.group(1);
                          }
                      } else {
                          value = null;
                      }
                  }

                  if (transform instanceof FieldTransform.FormatString) {
                      value = String.format(((FieldTransform.FormatString) transform).getFormatString(), value);
                  }

                  if (transform instanceof FieldTransform.PseudonymizeWithScope) {
                      Pseudonymizer scopedPseudonymizer = pseudonymizer;
                      if (pseudonymizer.getOptions().getPseudonymImplementation() == PseudonymImplementation.LEGACY) {
                          scopedPseudonymizer = pseudonymizers.computeIfAbsent(
                              ((FieldTransform.PseudonymizeWithScope) transform).getPseudonymizeWithScope(),
                              scope -> pseudonymizerImplFactory.create(pseudonymizer.getOptions().withDefaultScopeId(scope)));
                      }
                      value = pseudonymizationFunction.apply(value, pipeline.getNewName(), scopedPseudonymizer);
                  }

                  if (transform instanceof FieldTransform.JavaRegExpReplace) {
                      Matcher matcher = ((FieldTransform.JavaRegExpReplace) transform).getCompiledPattern().matcher(value);
                      if (matcher.matches()) {
                          value = matcher.replaceAll(((FieldTransform.JavaRegExpReplace) transform).getReplaceString());
                      }
                  }

                  if (transform instanceof FieldTransform.Pseudonymize) {
                      if (((FieldTransform.Pseudonymize) transform).isPseudonymize()) {
                          value = pseudonymizationFunction.apply(value, pipeline.getNewName(), pseudonymizer);
                      }
                  }
              }
          }
          return value;
        };


        // duplicated just copy the original value
        columnsToDuplicate.forEach((original, duplicated) -> addColumnTransform.accept(duplicated, original, Optional::ofNullable));
        // The table holds the transformation to be applied to each original column to produce the new column
        // we apply pseudonymization in the renamed columns
        columnsToRename.forEach((original, renamed) -> {
            addColumnTransform.accept(renamed, original, Optional::ofNullable);
        });
        // we apply the pipeline defined for each new column
        columnsToTransform.forEach((sourceColumn, pipelineSpec) -> pipelineSpec.forEach(pipeline -> {
            addColumnTransform.accept(pipeline.getNewName(), sourceColumn, (s) -> Optional.ofNullable(applyTransform.apply(s, pipeline)));
        }));
        // we apply pseudonymization in the pseudonymized columns
        columnsToPseudonymize.forEach(column -> {
            addColumnTransform.accept(column, column, (s) -> Optional.of(pseudonymizationFunction.apply(s, column, pseudonymizer)));
        });
        // we apply pseudonymization in the pseudonymized columns, only if present
        columnsToPseudonymizeIfPresent.forEach(column -> {
            if (headers.contains(column)) {
                addColumnTransform.accept(column, column, (s) -> Optional.of(pseudonymizationFunction.apply(s, column, pseudonymizer)));
            }
        });
        // all headers that are not in the table are copied as is
        headers.forEach(header -> {
            if (!columnTransforms.containsKey(header) && !columnsToRename.containsKey(header)) {
                // add it as is, no transformation, unless is a rename, we don't want the original
                addColumnTransform.accept(header, header, Optional::ofNullable);
            }
        });

        // we need to sort the columns in the order they appear in the file,
        // leave the headers with the original case (not sure why we would want to do this, but respect tests)
        // and new transformed columns after, in natural order to be consistent
        Comparator<String> originalHeadersOrRenamedFirst = Comparator.comparingInt(a -> ObjectUtils.min(
            records.getHeaderMap().getOrDefault(a, Integer.MAX_VALUE),
            columnsToRename.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(a)).findFirst().map(e -> records.getHeaderMap().getOrDefault(e.getKey(), Integer.MAX_VALUE)).orElse(Integer.MAX_VALUE)));
        Comparator<String> byColumnName = Comparator.naturalOrder();

        List<String> columnNamesForOutputFile = columnTransforms.keySet()
            .stream()
            .sorted(originalHeadersOrRenamedFirst.thenComparing(byColumnName))
            .collect(Collectors.toList())
            .stream()
            // leave original casing for headers
            .map( h -> headers.stream().filter(h::equalsIgnoreCase).findFirst().orElse(h))
            .collect(Collectors.toList());

        CSVFormat csvFormat = CSVFormat.Builder.create()
            .setHeader(columnNamesForOutputFile.toArray(new String[0]))
            .setRecordSeparator(records.getFirstEndOfLine())
            .build();

        // create an empty record to fill with the transformed values, ensuring all rows have
        // the same columns
        // linked hash map: need predictable iteration order and null values

        LinkedHashMap<String, String> newRecord = new LinkedHashMap<>();
        columnNamesForOutputFile.forEach(h -> newRecord.put(h, null));

        try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
            UnmodifiableIterator<List<CSVRecord>> chunks =
                Iterators.partition(records.iterator(), this.getRecordShuffleChunkSize());

            for (UnmodifiableIterator<List<CSVRecord>> chunkIterator = chunks; chunkIterator.hasNext(); ) {
                List<CSVRecord> chunk = new ArrayList<>(chunkIterator.next());

                shuffleImplementation.apply(chunk).forEach(record -> {

                    // clean up the record prior to use
                    newRecord.replaceAll((k, v) -> null);
                    newRecord.keySet().forEach(h -> {

                        Pair<String, List<Function<String, Optional<String>>>> transforms = columnTransforms.getOrDefault(h, null);
                        if (transforms == null) {
                            newRecord.put(h, null);
                        } else {
                            // apply all transformations in insertion order
                            // key holds the original column
                            String v = record.get(transforms.getKey());
                            if (StringUtils.isNotBlank(v)) {
                                for (Function<String, Optional<String>> transform : transforms.getValue()) {
                                    v = transform.apply(v).orElse(null);
                                }
                                newRecord.put(h, v);
                            } else {
                                newRecord.put(h, null);
                            }
                        }

                    });

                    try {
                        printer.printRecord(newRecord.values());
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
