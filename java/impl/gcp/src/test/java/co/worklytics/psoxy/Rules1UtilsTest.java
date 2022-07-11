package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.RulesUtils;
import dagger.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RulesUtilsTest {

    @Inject RulesUtils utils;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
    })
    public interface Container {
        void inject( RulesUtilsTest test);
    }

    @BeforeEach
    public void setup() {
        Container container = DaggerRulesUtilsTest_Container.create();
        container.inject(this);
    }

    @Test
    void getRulesFromConfig() {

        ConfigService config = mock(ConfigService.class);
        when(config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
            .thenReturn(Optional.of("YWxsb3dlZEVuZHBvaW50UmVnZXhlczoKICAtICJeL2dtYWlsL3YxL3VzZXJzL1teL10qPy9tZXNzYWdlcy4qIgplbWFpbEhlYWRlclBzZXVkb255bWl6YXRpb25zOgogIC0ganNvblBhdGhzOgogICAgICAtICIkLnBheWxvYWQuaGVhZGVyc1s/KEAubmFtZSA9fiAvXihGcm9tfFRvfENjfEJjYykkL2kpXS52YWx1ZSIKICAgIHJlbGF0aXZlVXJsUmVnZXg6ICJeL2dtYWlsL3YxL3VzZXJzLy4qPy9tZXNzYWdlcy8uKiIKcHNldWRvbnltaXphdGlvbnM6CiAgLSBqc29uUGF0aHM6CiAgICAgIC0gIiQucGF5bG9hZC5oZWFkZXJzWz8oQC5uYW1lID1+IC9eKFgtT3JpZ2luYWwtU2VuZGVyfERlbGl2ZXJlZC1Ub3xTZW5kZXJ8SW4tUmVwbHktVG8pJC9pKV0udmFsdWUiCiAgICByZWxhdGl2ZVVybFJlZ2V4OiAiXi9nbWFpbC92MS91c2Vycy8uKj8vbWVzc2FnZXMvLioiCnJlZGFjdGlvbnM6CiAgLSBqc29uUGF0aHM6CiAgICAgIC0gIiQucGF5bG9hZC5oZWFkZXJzWz8oIShALm5hbWUgPX4gL15Gcm9tfFRvfENjfEJjY3xYLU9yaWdpbmFsLVNlbmRlcnxEZWxpdmVyZWQtVG98U2VuZGVyfEluLVJlcGx5LVRvfE1lc3NhZ2UtSUR8RGF0ZXxPcmlnaW5hbC1NZXNzYWdlLUlEfFJlZmVyZW5jZXMkL2kpKV0iCiAgICByZWxhdGl2ZVVybFJlZ2V4OiAiXi9nbWFpbC92MS91c2Vycy8uKj8vbWVzc2FnZXMvLioiCg=="));

        RuleSet rules = utils.getRulesFromConfig(config).get();
        assertNotNull(rules);
    }
}
