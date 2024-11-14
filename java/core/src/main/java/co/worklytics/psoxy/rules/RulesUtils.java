package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.gateway.BulkModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.storage.StorageHandler;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.MultiTypeBulkDataRules;
import com.avaulta.gateway.rules.RecordRules;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;

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

            return Optional.of(parse(yamlEncodedRules));
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

    @VisibleForTesting
    String decodeToYaml(String valueFromConfig) {
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
            } catch (IOException e) {
                //ignore
            }
        }
        throw new RuntimeException("Failed to parse RULES from config");
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

    public Optional<String> getDefaultScopeIdFromRules(com.avaulta.gateway.rules.RuleSet ruleSet) {
        if (ruleSet instanceof RuleSet) {
            return Optional.ofNullable(((RuleSet) ruleSet).getDefaultScopeIdForSource());
        } else {
            return Optional.empty();
        }
    }

    static Map<String, String> defaultScopeIdBySource;
    static {
        defaultScopeIdBySource = new ConcurrentHashMap<>();
        defaultScopeIdBySource.put("gdirectory", "gapps");
        defaultScopeIdBySource.put("gcal", "gapps");
        defaultScopeIdBySource.put("gmail", "gapps");
        defaultScopeIdBySource.put("google-chat", "gapps");
        defaultScopeIdBySource.put("google-meet", "gapps");
        defaultScopeIdBySource.put("gdrive", "gapps");

        defaultScopeIdBySource.put("azure-ad", "azure-ad");
        defaultScopeIdBySource.put("outlook-cal", "azure-ad");
        defaultScopeIdBySource.put("outlook-mail", "azure-ad");
        defaultScopeIdBySource.put("msft-teams", "azure-ad");

        defaultScopeIdBySource.put("github", "github");
        defaultScopeIdBySource.put("github-enterprise-server", "github");
        defaultScopeIdBySource.put("slack", "slack");
        defaultScopeIdBySource.put("zoom", "zoom");
        defaultScopeIdBySource.put("salesforce", "salesforce");
        defaultScopeIdBySource.put("jira-server", "jira");
        defaultScopeIdBySource.put("jira-cloud", "jira");
        defaultScopeIdBySource.put("dropbox-business", "dropbox-business");

        // we expect hris (employee_id) scope in all bulk data sources
        defaultScopeIdBySource.put("hris", "hris");
        defaultScopeIdBySource.put("badge", "hris");
        defaultScopeIdBySource.put("survey", "hris");
        defaultScopeIdBySource.put("qualtrics", "hris");
    }

    public String getDefaultScopeIdFromSource(@NonNull String source) {
        String defaultScopeId = defaultScopeIdBySource.get(source);

        if (defaultScopeId == null) {
            log.warning("No defaultScopeId set for source; failing to source itself: " + source);
            defaultScopeId = source;
        }
        return defaultScopeId;
    }
}