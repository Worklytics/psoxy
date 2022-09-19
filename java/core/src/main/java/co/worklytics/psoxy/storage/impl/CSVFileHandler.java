package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.rules.CsvRules;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.storage.FileHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles a CSV file to apply the rules pseudonymize the content.
 * CSV should have the first row with headers and being separated with commas; content should be quoted
 * if include commas or quotes inside.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class CSVFileHandler implements FileHandler {

    @Inject
    ObjectMapper objectMapper;


    @Override
    public byte[] handle(@NonNull InputStreamReader reader, @NonNull Sanitizer sanitizer) throws IOException {
        CSVParser records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .withIgnoreHeaderCase()
                .withTrim()
                .parse(reader);

        Preconditions.checkArgument(records.getHeaderMap() != null, "Failed to parse header from file");

        Sanitizer.ConfigurationOptions configurationOptions = sanitizer.getConfigurationOptions();

        Set<String> columnsToRedact = trimIntoSet(((CsvRules) configurationOptions.getRules())
            .getColumnsToRedact());

        Set<String> columnsToPseudonymize = trimIntoSet(((CsvRules) configurationOptions.getRules())
            .getColumnsToPseudonymize());

        final Map<String, String> columnsToRename = ((CsvRules) configurationOptions.getRules())
            .getColumnsToRename()
            .entrySet().stream()
            .collect(Collectors.toMap(
                entry -> entry.getKey().trim(),
                entry -> entry.getValue().trim(),
                (a, b) -> a,
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));

        // headers respecting insertion order
        // when constructing the parser with ignore header case the keySet may not return values in
        // order. header map is <key, position>, order by position first, then construct the key set
        Set<String> headers = records.getHeaderMap()
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .filter(entry -> !columnsToRedact.contains(entry.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // case-insensitive headers
        Set<String> headersCI = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        headersCI.addAll(headers);

        // precondition, everything expected to be pseudonymized must exist in the rest of columns
        Set<String> outputColumnsCI = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        outputColumnsCI.addAll(applyReplacements(headersCI, columnsToRename));
        Sets.SetView<String> missingColumnsToPseudonymize =
            Sets.difference(columnsToPseudonymize, outputColumnsCI);
        if (!missingColumnsToPseudonymize.isEmpty()) {
            throw new IllegalArgumentException(String.format("Columns to pseudonymize (%s) missing from set found in file (%s)",
                "\"" + String.join("\",\"", missingColumnsToPseudonymize) + "\"",
                "\"" + String.join("\",\"", headersCI) + "\""));
        }


        try(ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            PrintWriter printWriter = new PrintWriter(baos);
            CSVPrinter printer = new CSVPrinter(printWriter, CSVFormat.DEFAULT
                .withHeader(applyReplacements(headers, columnsToRename).toArray(new String[0])))
            ) {

            records.forEach(row -> {
                List<Object> sanitized = headers.stream() // only iterate on allowed headers
                        .map(column -> {
                            String value = row.get(column);

                            String outputColumnName = columnsToRename.getOrDefault(column, column);

                            if (columnsToPseudonymize.contains(outputColumnName)) {
                                if (StringUtils.isNotBlank(value)) {
                                    try {
                                        return objectMapper.writeValueAsString(sanitizer.pseudonymize(value));
                                    } catch (JsonProcessingException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            }
                            return value;
                        })
                        .collect(Collectors.toList());

                try {
                    printer.printRecord(sanitized);
                } catch (Throwable e) {
                    throw new RuntimeException("Failed to write row", e);
                }
            });

            printWriter.flush();

            return baos.toByteArray();
        }
    }

    List<String> applyReplacements(Collection<String> original, final Map<String, String> replacements) {
        return original.stream()
            .map(value -> replacements.getOrDefault(value, value))
            .collect(Collectors.toList());
    }


    Set<String> trimIntoSet(Collection<String> set) {
        return set.stream()
            .map(String::trim)
            .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    }

}
