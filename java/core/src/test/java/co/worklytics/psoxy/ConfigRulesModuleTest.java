package co.worklytics.psoxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.ResourceService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.RulesUtils;
import com.avaulta.gateway.rules.RuleSet;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigRulesModuleTest {

    private Logger logger;
    private RulesUtils rulesUtils;
    private ConfigService config;
    private EnvVarsConfigService envVarsConfigService;
    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger(ConfigRulesModuleTest.class.getName());
        rulesUtils = mock(RulesUtils.class);
        config = mock(ConfigService.class);
        envVarsConfigService = mock(EnvVarsConfigService.class);
        resourceService = mock(ResourceService.class);
    }

    @Test
    void rules_loadedFromEnvConfig() {
        RuleSet mockRuleSet = mock(RuleSet.class);
        when(rulesUtils.getRulesFromConfig(config, envVarsConfigService)).thenReturn(Optional.of(mockRuleSet));

        RuleSet resolved = ConfigRulesModule.rules(logger, rulesUtils, config, envVarsConfigService, resourceService);

        assertEquals(mockRuleSet, resolved);
        verifyNoInteractions(resourceService);
        verify(config, never()).getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE);
    }

    @Test
    void rules_loadedFromResource() throws Exception {
        RuleSet mockRuleSet = mock(RuleSet.class);
        when(rulesUtils.getRulesFromConfig(config, envVarsConfigService)).thenReturn(Optional.empty());

        byte[] yamlBytes = "dummy yaml".getBytes();
        InputStream inputStream = new ByteArrayInputStream(yamlBytes);
        when(resourceService.getResource(ConfigRulesModule.RULES_RESOURCE_PATH)).thenReturn(Optional.of(inputStream));
        when(rulesUtils.parse("dummy yaml")).thenReturn(mockRuleSet);

        RuleSet resolved = ConfigRulesModule.rules(logger, rulesUtils, config, envVarsConfigService, resourceService);

        assertEquals(mockRuleSet, resolved);
        verify(resourceService).getResource(ConfigRulesModule.RULES_RESOURCE_PATH);
        verify(config, never()).getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE);
    }

    @Test
    void rules_fallbackToDefaults() {
        when(rulesUtils.getRulesFromConfig(config, envVarsConfigService)).thenReturn(Optional.empty());
        when(resourceService.getResource(ConfigRulesModule.RULES_RESOURCE_PATH)).thenReturn(Optional.empty());

        when(config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE)).thenReturn(Optional.of("gmail"));
        when(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYMIZE_APP_IDS)).thenReturn(Optional.of("false"));

        RuleSet resolved = ConfigRulesModule.rules(logger, rulesUtils, config, envVarsConfigService, resourceService);

        assertNotNull(resolved);
        assertTrue(resolved instanceof RESTRules);
        verify(resourceService).getResource(ConfigRulesModule.RULES_RESOURCE_PATH);
        verify(config).getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE);
    }

    @Test
    void getRulesFromResource_returnsEmptyOnResourceServiceException() {
        when(resourceService.getResource(ConfigRulesModule.RULES_RESOURCE_PATH)).thenThrow(new RuntimeException("Service failed"));

        Optional<RuleSet> resolved = ConfigRulesModule.getRulesFromResource(logger, rulesUtils, resourceService);

        assertTrue(resolved.isEmpty());
    }
}
