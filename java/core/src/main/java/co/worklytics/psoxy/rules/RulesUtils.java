package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.codec.digest.DigestUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

@Log
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
        Optional<String> configuredRules = config.getConfigPropertyAsOptional(ProxyConfigProperty.RULES);

        if (configuredRules.isPresent()) {
            String yamlEncodedRules;
            try {
                yamlEncodedRules = new String(Base64.getDecoder().decode(configuredRules.get()));
                log.info("RULES configured as base64-encoded YAML");
            } catch (IllegalArgumentException e) {
                yamlEncodedRules = configuredRules.get();
            }
            return Optional.of(parse(yamlEncodedRules));
        } else {
            return Optional.empty();
        }
    }

    @VisibleForTesting
    RuleSet parse(@NonNull String yamlString) {
        try {
            CsvRules rules = yamlMapper.readerFor(CsvRules.class).readValue(yamlString);
            Validator.validate(rules);
            log.info("Rules parsed as CsvRules");
            return rules;
        } catch (IOException e) {
            try {
                Rules2 rules = yamlMapper.readerFor(Rules2.class).readValue(yamlString);
                Validator.validate(rules);
                log.info("Rules parsed as Rules2");
                return rules;
            } catch (IOException ex) {
                throw new IllegalStateException("Invalid rules configured", ex);
            }
        }
    }

}
