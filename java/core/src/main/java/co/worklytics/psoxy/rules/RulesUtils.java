package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

@NoArgsConstructor(onConstructor_ = @Inject)
public class RulesUtils {

    @Inject @Named("ForYAML")
    ObjectMapper yamlMapper;

    @SneakyThrows
    public String sha(RuleSet rules) {
        return DigestUtils.sha1Hex(yamlMapper.writeValueAsString(rules));
    }

    /**
     * @return rules parsed from config, presumed to be base64-encoded YAML
     * @throws IllegalArgumentException if config property's value is not valid base64
     * @throws IOException if can't parse YAML --> Java object, specifically:
     * @see com.fasterxml.jackson.core.JsonParseException sry for the misnomer, but we leverage Jackson for both YAML and JSON
     */
    @SneakyThrows
    public Optional<RuleSet> getRulesFromConfig(ConfigService config) {
        return config.getConfigPropertyAsOptional(ProxyConfigProperty.RULES)
            .map(base64encoded -> Base64.getDecoder().decode(base64encoded))
            .map(yamlString -> {
                try {
                    CsvRules rules = yamlMapper.readerFor(CsvRules.class).readValue(yamlString);
                    Validator.validate(rules);
                    return rules;
                } catch (IOException e) {
                    try {
                        Rules2 rules = yamlMapper.readerFor(Rules2.class).readValue(yamlString);
                        Validator.validate(rules);
                        return rules;
                    } catch (IOException ex) {
                        throw new IllegalStateException("Invalid rules configured", e);
                    }
                }
            });
    }

}
