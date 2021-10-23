package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.SanitizerImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {

    private static final String DEFAULT_CONFIG_FILE = "config.yaml";

    static ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    static ObjectMapper jsonMapper = new ObjectMapper();



    @lombok.SneakyThrows
    public static void main(String[] args) {

        File configFile = new File(DEFAULT_CONFIG_FILE);
        Config config;
        if (configFile.exists()) {
            config = yamlMapper.readerFor(Config.class).readValue(configFile);
        } else {
            throw new Error("No config.yaml found");
        }

        File inputFile = new File(args[0]);

        Preconditions.checkArgument(inputFile.exists(), "File %s does not exist", args[0]);

        main(config, inputFile, System.out);
    }

    @SneakyThrows
    public static void main(Config config, File inputFile, Appendable out) {

        Sanitizer sanitizer = new SanitizerImpl(Sanitizer.Options.builder()
            .defaultScopeId(config.getDefaultScopeId())
            .pseudonymizationSalt(config.getPseudonymizationSalt())
            .build());

        try (FileReader in = new FileReader(inputFile)) {
            CSVParser records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(in);

            String[] header = records.getHeaderMap().entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList())
                .toArray(new String[records.getHeaderMap().size()]);

            config.getColumnsToPseudonymize().stream()
                    .forEach(columnToPseudonymize ->
                        Preconditions.checkArgument(records.getHeaderMap().containsKey(columnToPseudonymize), "Column %s to be pseudonymized not in file", columnToPseudonymize));

            CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(header));

            records.forEach(row -> {

                List<Object> sanitized = records.getHeaderMap().entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(column -> {
                        if (config.getColumnsToPseudonymize().contains(column.getKey())) {
                            try {
                                //q: need extra escaping??
                                return jsonMapper.writeValueAsString(sanitizer.pseudonymize(row.get(column.getValue())));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            return row.get(column.getValue());
                        }
                    })
                    .collect(Collectors.toList());


                try {
                    printer.printRecord(sanitized);
                } catch (Throwable e) {
                    throw new RuntimeException("Failed to write row", e);
                }
            });
        }
    }

}
