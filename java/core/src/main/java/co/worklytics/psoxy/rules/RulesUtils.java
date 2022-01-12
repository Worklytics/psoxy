package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

@NoArgsConstructor(onConstructor_ = @Inject)
public class RulesUtils {

    @Inject @Named("forYaml")
    ObjectMapper yamlMapper;

    @SneakyThrows
    public String sha(Rules rules) {
        return DigestUtils.sha1Hex(yamlMapper.writeValueAsString(rules));
    }


    /**
     * provides option to for customers to override rules for a source with custom ones defined in a
     * YAML file. Not really recommended, as complicates deployment (must add the file to
     * `target/deployment` before calling the gcloud deploy cmmd), but we provide the option should
     * advanced users want more control.
     *
     * @param pathToRulesFile path to rules file
     * @return rules, if defined, from file system
     */
    @SneakyThrows
    public Optional<Rules> getRulesFromFileSystem(String pathToRulesFile) {
        File rulesFile = new File(pathToRulesFile);
        if (rulesFile.exists()) {
            Rules rules = yamlMapper.readerFor(Rules.class).readValue(rulesFile);
            Validator.validate(rules);
            return Optional.of(rules);
        }
        return Optional.empty();
    }


    /**
     * @return rules parsed from config, presumed to be base64-encoded YAML
     * @throws IllegalArgumentException if config property's value is not valid base64
     * @throws IOException if can't parse YAML --> Java object, specifically:
     * @see com.fasterxml.jackson.core.JsonParseException sry for the misnomer, but we leverage Jackson for both YAML and JSON
     */
    @SneakyThrows
    public Optional<Rules> getRulesFromConfig(ConfigService config) {
        return config.getConfigPropertyAsOptional(ProxyConfigProperty.RULES)
            .map(base64encoded -> Base64.getDecoder().decode(base64encoded))
            .map(yamlString -> {
                try {
                    Rules rules = yamlMapper.readerFor(Rules.class).readValue(yamlString);
                    Validator.validate(rules);
                    return rules;
                } catch (IOException e) {
                    throw new IllegalStateException("Invalid rules configured", e);
                }
            });
    }

}
