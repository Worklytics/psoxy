package co.worklytics.psoxy;

import com.google.common.base.Preconditions;

import java.io.File;

public class Main {

    private static final String DEFAULT_CONFIG_FILE = "config.yaml";


    @lombok.SneakyThrows
    public static void main(String[] args) {
        Preconditions.checkArgument(args.length != 0, "No filename passed; please invoke as: java -jar target/psoxy-cmd-line-1.0-SNAPSHOT.jar fileToPseudonymize.csv");
        Preconditions.checkArgument(args.length < 2, "Too many arguments passed; please invoke as: java -jar target/psoxy-cmd-line-1.0-SNAPSHOT.jar fileToPseudonymize.csv");

        File configFile = new File(DEFAULT_CONFIG_FILE);


        CmdLineContainer container = DaggerCmdLineContainer.create();

        Config config;
        if (configFile.exists()) {
            config = container.yamlMapper().readerFor(Config.class).readValue(configFile);
            Config.validate(config);
        } else {
            throw new Error("No config.yaml found");
        }

        File inputFile = new File(args[0]);

        Preconditions.checkArgument(inputFile.exists(), "File %s does not exist", args[0]);

        container.fileHandler().sanitize(config, inputFile, System.out);
    }



}
