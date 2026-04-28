package co.worklytics.psoxy.rules;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.MultiTypeBulkDataRules;
import com.avaulta.gateway.rules.RecordRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import co.worklytics.psoxy.ErrorCauses;
import co.worklytics.psoxy.gateway.BulkModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.storage.StorageHandler;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

@Log
@NoArgsConstructor(onConstructor_ = @Inject)
public class RulesUtils {

    @Inject @Named("ForYAML")
    ObjectMapper yamlMapper;

    @Inject
    Validator validator;

    @SneakyThrows
    public String sha(com.avaulta.gateway.rules.RuleSet rules) {
        return DigestUtils.sha1Hex(asYaml(rules));
    }

    @SneakyThrows
    public String asYaml(com.avaulta.gateway.rules.RuleSet rules) {
        return yamlMapper.writeValueAsString(rules);
    }

    /**
     * @return rules parsed from config, presumed to be base64-encoded YAML
     * @throws IllegalArgumentException if config property's value is not valid base64
     * @throws IOException if can't parse YAML --> Java object, specifically:
     * @see com.fasterxml.jackson.core.JsonParseException sry for the misnomer, but we leverage Jackson for both YAML and JSON
     */
    @SneakyThrows
    public Optional<com.avaulta.gateway.rules.RuleSet> getRulesFromConfig(ConfigService config,
                                                                          EnvVarsConfigService envVarsConfigService) {
        Optional<String> configuredRules = config.getConfigPropertyAsOptional(ProxyConfigProperty.RULES);

        if (configuredRules.isPresent()) {
            String yamlEncodedRules = decodeToYaml(configuredRules.get());

            //NOTE: could check CUSTOM_RULES_SHA here, but that's only filled when customers fill
            // the custom rules via Terraform; and may drift if customer later alters rules via
            // some other mechanism (eg, directly from the SSM Parameter Store UX in AWS Console),
            // which is fairly legitimate

            try {
                return Optional.of(parse(yamlEncodedRules));
            } catch (InvalidRulesException e) {
                if (!yamlEncodedRules.equals(configuredRules.get())) {
                    try {
                        return Optional.of(parse(configuredRules.get()));
                    } catch (InvalidRulesException ignored) {
                        // ignore this fallback exception, throw original
                    }
                }
                throw e;
            }
        } else {
            if (envVarsConfigService != null // legacy case
                && envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.CUSTOM_RULES_SHA).map(StringUtils::isNotBlank).orElse(false)) {
                //possible cases
                //  - Terraform bug where SHA filled in env vars,
                //  - RULES set in GCP Secret Manager/ AWS Parameter Store / etc but

                log.warning("CUSTOM_RULES_SHA is set, but no RULES are configured; ensure that RULES configuration value is populated and accessible to the Proxy instance");
            }

            return Optional.empty();
        }
    }

    public String decodeToYaml(String valueFromConfig) {
        //possible encodings: 1) base64-encoded YAML, 2) plain YAML, 3) base64-encoded gzipped YAML
        String yamlEncodedRules;
        try {
            byte[] raw = Base64.getDecoder().decode(valueFromConfig);

            try {
                raw = ByteStreams.toByteArray(new GZIPInputStream(new ByteArrayInputStream(raw)));
                log.info("RULES configured as base64-encoded gzipped YAML");
            } catch (IOException e) {
                //not gzipped
                log.info("RULES configured as base64-encoded YAML");
            }
            yamlEncodedRules = new String(raw);
        } catch (IllegalArgumentException e) {
            log.info("RULES configured as YAML");
            yamlEncodedRules = valueFromConfig;
        }
        return yamlEncodedRules;
    }

    final static List<Class<? extends com.avaulta.gateway.rules.RuleSet>> rulesImplementations = Arrays.asList(
        ColumnarRules.class,
        Rules2.class,
        MultiTypeBulkDataRules.class,
        RecordRules.class
    );


    @VisibleForTesting
    public com.avaulta.gateway.rules.RuleSet parse(@NonNull String yamlString) {
        for (Class<?> impl : rulesImplementations) {
            try {
                com.avaulta.gateway.rules.RuleSet rules =
                    yamlMapper.readerFor(impl).readValue(yamlString);
                log.log(Level.INFO, "RULES parsed as {0}", impl.getSimpleName());
                validator.validate(rules);
                return rules;
            } catch (com.fasterxml.jackson.core.JsonParseException | com.fasterxml.jackson.databind.JsonMappingException e) {
                // If it's the last implementation, throw the error
                if (impl == Iterables.getLast(rulesImplementations)) {
                    ErrorCauses errorCause = (e instanceof com.fasterxml.jackson.core.JsonParseException) ? 
                        ErrorCauses.RULES_INVALID_YAML : ErrorCauses.RULES_INVALID;
                    throw new InvalidRulesException("Failed to parse RULES from config", e, errorCause);
                }
            } catch (IOException e) {
                // Ignore other IO exceptions and try next impl
                if (impl == Iterables.getLast(rulesImplementations)) {
                    throw new InvalidRulesException("Failed to read RULES from config", e, ErrorCauses.CONFIGURATION_FAILURE);
                }
            }
        }
        throw new InvalidRulesException("Failed to parse RULES from config", ErrorCauses.RULES_INVALID);
    }

    public List<StorageHandler.ObjectTransform> parseAdditionalTransforms(ConfigService config) {
        Optional<String> additionalTransforms = config.getConfigPropertyAsOptional(BulkModeConfigProperty.ADDITIONAL_TRANSFORMS);
        CollectionType type = yamlMapper.getTypeFactory().constructCollectionType(ArrayList.class, StorageHandler.ObjectTransform.class);

        if (additionalTransforms.isPresent()) {
            try {
                return yamlMapper.readValue(additionalTransforms.get(), type);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse ADDITIONAL_TRANSFORMS from config", e);
            }
        } else {
            return new ArrayList<>();
        }
    }
}
