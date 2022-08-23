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
            .thenReturn(Optional.of("YWxsb3dBbGxFbmRwb2ludHM6IGZhbHNlCmVuZHBvaW50czoKICAtIHBhdGhSZWdleDogL2NhbGVuZGFyL3YzL2NhbGVuZGFycy8uKi9ldmVudHMuKgogICAgdHJhbnNmb3JtczoKICAgICAgLSAhPHBzZXVkb255bWl6ZT4KICAgICAgICBqc29uUGF0aHM6CiAgICAgICAgICAtICQuLmVtYWlsCiAgICAgIC0gITxyZWRhY3Q+CiAgICAgICAganNvblBhdGhzOgogICAgICAgICAgLSAkLi5kaXNwbGF5TmFtZQogICAgICAgICAgLSAkLml0ZW1zWypdLmV4dGVuZGVkUHJvcGVydGllcy5wcml2YXRlCg=="));

        RuleSet rules = utils.getRulesFromConfig(config).get();
        assertNotNull(rules);
    }
}
