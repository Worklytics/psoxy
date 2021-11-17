package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.SanitizerImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Main {

    private static final String DEFAULT_CONFIG_FILE = "config.yaml";

    static ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    static ObjectMapper jsonMapper = new ObjectMapper();



    @lombok.SneakyThrows
    public static void main(String[] args) {
        Preconditions.checkArgument(args.length != 0, "No filename passed; please invoke as: java -jar target/psoxy-cmd-line-1.0-SNAPSHOT.jar fileToPseudonymize.csv");
        Preconditions.checkArgument(args.length < 2, "Too many arguments passed; please invoke as: java -jar target/psoxy-cmd-line-1.0-SNAPSHOT.jar fileToPseudonymize.csv");

        File configFile = new File(DEFAULT_CONFIG_FILE);
        Config config;
        if (configFile.exists()) {
            config = yamlMapper.readerFor(Config.class).readValue(configFile);
            Config.validate(config);
        } else {
            throw new Error("No config.yaml found");
        }

        File inputFile = new File(args[0]);

        Preconditions.checkArgument(inputFile.exists(), "File %s does not exist", args[0]);

        pseudonymize(config, inputFile, System.out);
    }

    @SneakyThrows
    public static void pseudonymize(Config config, File inputFile, Appendable out) {
        Sanitizer.Options.OptionsBuilder options = Sanitizer.Options.builder()
            .defaultScopeId(config.getDefaultScopeId());

        if (config.getPseudonymizationSaltSecret() != null) {
            try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
                AccessSecretVersionResponse secretVersionResponse =
                    client.accessSecretVersion(config.getPseudonymizationSaltSecret().getIdentifier());
                options.pseudonymizationSalt(secretVersionResponse.getPayload().getData().toStringUtf8());
            }
        } else {
            options.pseudonymizationSalt(config.getPseudonymizationSalt());
        }


        Sanitizer sanitizer = new SanitizerImpl(options.build());

        try (FileReader in = new FileReader(inputFile)) {
            CSVParser records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(in);

            Preconditions.checkArgument(records.getHeaderMap() != null, "Failed to parse header from file");

            Map<Integer, String> invertedHeaderMap = records.getHeaderMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a, TreeMap::new));

            String[] header = invertedHeaderMap.values().toArray(new String[invertedHeaderMap.size()]);

            config.getColumnsToPseudonymize().stream()
                    .forEach(columnToPseudonymize ->
                        Preconditions.checkArgument(records.getHeaderMap().containsKey(columnToPseudonymize), "Column %s to be pseudonymized not in file", columnToPseudonymize));

            CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(header));

            records.forEach(row -> {

                List<Object> sanitized = invertedHeaderMap.entrySet()
                    .stream()
                    .map(column -> {
                        if (config.getColumnsToPseudonymize().contains(column.getValue())) {
                            try {
                                //q: need extra escaping??
                                return jsonMapper.writeValueAsString(sanitizer.pseudonymize(row.get(column.getKey())));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            return row.get(column.getKey());
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
