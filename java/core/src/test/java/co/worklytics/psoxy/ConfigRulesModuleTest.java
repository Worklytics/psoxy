package co.worklytics.psoxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.avaulta.gateway.resources.ResourceService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.test.MockModules;
import com.avaulta.gateway.rules.RuleSet;
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
        rulesUtils = MockModules.provideMock(RulesUtils.class);
        config = MockModules.provideMock(ConfigService.class);
        envVarsConfigService = MockModules.provideMock(EnvVarsConfigService.class);
        resourceService = MockModules.provideMock(ResourceService.class);
    }

    @Test
    void rules_loadedFromEnvConfig() {
        RuleSet mockRuleSet = MockModules.provideMock(RuleSet.class);
        when(rulesUtils.getRulesFromConfig(config, envVarsConfigService)).thenReturn(Optional.of(mockRuleSet));

        RuleSet resolved = ConfigRulesModule.rules(logger, rulesUtils, config, envVarsConfigService, resourceService);

        assertEquals(mockRuleSet, resolved);
        verifyNoInteractions(resourceService);
        verify(config, never()).getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE);
    }

    @Test
    void rules_loadedFromResource() {
        RuleSet mockRuleSet = MockModules.provideMock(RuleSet.class);
        when(rulesUtils.getRulesFromConfig(config, envVarsConfigService)).thenReturn(Optional.empty());
        when(rulesUtils.getRulesFromResource(resourceService)).thenReturn(Optional.of(mockRuleSet));

        RuleSet resolved = ConfigRulesModule.rules(logger, rulesUtils, config, envVarsConfigService, resourceService);

        assertEquals(mockRuleSet, resolved);
        verify(rulesUtils).getRulesFromResource(resourceService);
        verify(config, never()).getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE);
    }

    @Test
    void rules_fallbackToDefaults() {
        when(rulesUtils.getRulesFromConfig(config, envVarsConfigService)).thenReturn(Optional.empty());
        when(rulesUtils.getRulesFromResource(resourceService)).thenReturn(Optional.empty());

        when(config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE)).thenReturn(Optional.of("gmail"));
        when(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYMIZE_APP_IDS)).thenReturn(Optional.of("false"));

        RuleSet resolved = ConfigRulesModule.rules(logger, rulesUtils, config, envVarsConfigService, resourceService);

        assertNotNull(resolved);
        assertTrue(resolved instanceof RESTRules);
        verify(rulesUtils).getRulesFromResource(resourceService);
        verify(config).getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE);
    }

    @Test
    void getRulesFromResource_throwsOnResourceServiceException() {
        RulesUtils realRulesUtils = new RulesUtils();
        when(resourceService.getResource(RulesUtils.RULES_RESOURCE_PATH)).thenThrow(new RuntimeException("Service failed"));

        assertThrows(RuntimeException.class,
            () -> realRulesUtils.getRulesFromResource(resourceService));
    }

    @Test
    void rules_doesNotFallbackToDefaultsWhenResourceRulesAreInvalid() {
        when(rulesUtils.getRulesFromConfig(config, envVarsConfigService)).thenReturn(Optional.empty());
        when(rulesUtils.getRulesFromResource(resourceService)).thenThrow(new RuntimeException("Invalid resource rules"));

        assertThrows(RuntimeException.class,
            () -> ConfigRulesModule.rules(logger, rulesUtils, config, envVarsConfigService, resourceService));
        verify(config, never()).getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE);
    }
}
