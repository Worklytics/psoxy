package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.SneakyThrows;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * config service that either takes its configuration from the command line or from a file

 */
public class CommandLineConfigService implements ConfigService, SecretStore {


    private static final String DEFAULT_CONFIG_FILE = "config.yaml";

    static final Map<ConfigService.ConfigProperty, String> CONFIG_PROPERTY_TO_CLI_MAP = Map.of(
        ProxyConfigProperty.PSOXY_SALT, "salt"
    );

    @Inject @Named("ForYAML")
    ObjectMapper yamlMapper;

    @Inject
    EnvVarsConfigService envVarsConfigService;

    CommandLine cmd;


    @SneakyThrows
    @AssistedInject
    CommandLineConfigService(@Assisted String[] args) {
        Options options = new Options();

        // Add various options
        options.addOption("s", "salt", true, "Salt to use when generating pseudonyms");
        options.addOption("r", "redact", true, "Columns to redact");
        options.addOption("p", "pseudonymize", true, "Columns to pseudonymize");

        CommandLineParser parser = new DefaultParser();
        cmd = parser.parse(options, args);
    }


    Config parsedConfigFile;

    @SneakyThrows
    Optional<Config> getConfigFromFile() {
        if (parsedConfigFile== null) {
            File configFile = new File(DEFAULT_CONFIG_FILE);
            if (configFile.exists()) {
                parsedConfigFile = yamlMapper.readerFor(Config.class).readValue(new File(DEFAULT_CONFIG_FILE));
                Config.validate(parsedConfigFile);
            }
        }
        return Optional.ofNullable(parsedConfigFile);
    }


    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return this.getConfigPropertyAsOptional(property).orElseThrow(() -> new IllegalArgumentException("Property " + property + " not found"));
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {

        if (CONFIG_PROPERTY_TO_CLI_MAP.containsKey(property)) {
            if (cmd.hasOption(CONFIG_PROPERTY_TO_CLI_MAP.get(property))) {
                return Optional.ofNullable(cmd.getOptionValue(CONFIG_PROPERTY_TO_CLI_MAP.get(property)));
            }
        }

        //failover to config file
        if (property == ProxyConfigProperty.PSOXY_SALT) {
            return getConfigFromFile().map(Config::getPseudonymizationSalt);
        }

        //failover to env vars for everything else
        return envVarsConfigService.getConfigPropertyAsOptional(property);
    }


    public Config getCliConfig() {
        return getConfigFromFile().orElseGet(() -> {
            Config.ConfigBuilder builder = Config.builder().pseudonymizationSalt(getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT));

            if (cmd.hasOption("p")) {
                builder.columnsToPseudonymize(Arrays.stream(cmd.getOptionValues("p")).collect(Collectors.toSet()));
            }

            if (cmd.hasOption("r")) {
                builder.columnsToRedact(Arrays.stream(cmd.getOptionValues("r")).collect(Collectors.toSet()));

            }
            return builder.build();
        });
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public String getFileToProcess() {
        if (cmd.getArgList().isEmpty()) {
            throw new IllegalArgumentException("No file to process");
        }
        return cmd.getArgList().get(0);
    }
}
