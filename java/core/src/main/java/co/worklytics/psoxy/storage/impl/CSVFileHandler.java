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

        Map<Integer, String> invertedHeaderMap = records.getHeaderMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a, TreeMap::new));

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

        String[] header = invertedHeaderMap.values()
                .stream()
                .filter(entry -> !columnsToRedact.contains(entry)).toArray(String[]::new);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        PrintWriter printWriter = new PrintWriter(baos);
        CSVPrinter printer = new CSVPrinter(printWriter, CSVFormat.DEFAULT.withHeader(header));

        columnsToPseudonymize
                .forEach(columnToPseudonymize ->
                        Preconditions.checkArgument(records.getHeaderMap().containsKey(columnToPseudonymize), "Column %s to be pseudonymized not in file", columnToPseudonymize));

        records.forEach(row -> {
            List<Object> sanitized = invertedHeaderMap.entrySet()
                    .stream()
                    .map(column -> {
                        String value = row.get(column.getKey());
                        if (columnsToRedact.contains(column.getValue())) {
                            return null;
                        } else if (StringUtils.isNotBlank(value) && columnsToPseudonymize.contains(column.getValue())) {
                            try {
                                return objectMapper.writeValueAsString(sanitizer.pseudonymize(value));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            return value;
                        }
                    })
                    .filter(Objects::nonNull)
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
