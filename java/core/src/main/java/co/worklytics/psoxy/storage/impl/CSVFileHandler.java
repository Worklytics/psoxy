package co.worklytics.psoxy.storage.impl;

import co.worklytics.psoxy.storage.FileHandler;
import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Sanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
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
                .parse(reader);

        Preconditions.checkArgument(records.getHeaderMap() != null, "Failed to parse header from file");

        Sanitizer.Options options = sanitizer.getOptions();

        Set<String> columnsToRedact = options.getRules()
                .getRedactions()
                .stream()
                .map(Rules.Rule::getCsvColumns)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<String> columnsToPseudonymize = options.getRules()
                .getPseudonymizations()
                .stream()
                .map(Rules.Rule::getCsvColumns)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        String[] header = records.getHeaderMap().keySet()
                .stream()
                .filter(entry -> !columnsToRedact.contains(entry)).toArray(String[]::new);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        PrintWriter printWriter = new PrintWriter(baos);
        CSVPrinter printer = new CSVPrinter(printWriter, CSVFormat.DEFAULT.withHeader(header));

        columnsToPseudonymize
                .forEach(columnToPseudonymize ->
                        Preconditions.checkArgument(records.getHeaderMap().containsKey(columnToPseudonymize), "Column %s to be pseudonymized not in file", columnToPseudonymize));

        records.forEach(row -> {
            List<Object> sanitized = Arrays.stream(header) // only iterate on allowed headers
                    .map(column -> {
                        String value = row.get(column);
                        if (columnsToPseudonymize.contains(column)) {
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
