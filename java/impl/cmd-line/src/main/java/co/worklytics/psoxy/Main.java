package co.worklytics.psoxy;

import com.google.common.base.Preconditions;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Main {

    @lombok.SneakyThrows
    public static void main(String[] args) {

        CmdLineContainer container = DaggerCmdLineContainer.builder()
                .cmdLineModule(new CmdLineModule(args))
                .build();


        CommandLineConfigService configService = container.commandLineConfigServiceFactory().create(args);

        File inputFile = new File(configService.getFileToProcess());

        Preconditions.checkArgument(inputFile.exists(), "File %s does not exist", args[0]);

        container.fileHandler().sanitize(configService.getCliConfig(), inputFile, System.out);
    }

}
