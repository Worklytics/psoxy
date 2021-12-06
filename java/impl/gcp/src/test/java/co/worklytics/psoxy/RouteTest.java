package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.rules.Validator;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteTest {

    @Test
    void getRulesFromFileSystem() {
        Route route = new Route();

        String path = getClass().getResource("/rules/example.yaml").getPath();


        Optional<Rules> rules = route.getRulesFromFileSystem(Optional.of(path));

        assertTrue(rules.isPresent());
        Validator.validate(rules.get());
    }

    @Test
    void getRulesFromConfig() {
        Route route = new Route();

        route.config = mock(ConfigService.class);
        when(route.config.getConfigPropertyAsOptional(eq(ProxyConfigProperty.RULES)))
            .thenReturn(Optional.of("YWxsb3dlZEVuZHBvaW50UmVnZXhlczoKICAtICJeL2dtYWlsL3YxL3VzZXJzL1teL10qPy9tZXNzYWdlcy4qIgplbWFpbEhlYWRlclBzZXVkb255bWl6YXRpb25zOgogIC0ganNvblBhdGhzOgogICAgICAtICIkLnBheWxvYWQuaGVhZGVyc1s/KEAubmFtZSA9fiAvXihGcm9tfFRvfENjfEJjYykkL2kpXS52YWx1ZSIKICAgIHJlbGF0aXZlVXJsUmVnZXg6ICJeL2dtYWlsL3YxL3VzZXJzLy4qPy9tZXNzYWdlcy8uKiIKcHNldWRvbnltaXphdGlvbnM6CiAgLSBqc29uUGF0aHM6CiAgICAgIC0gIiQucGF5bG9hZC5oZWFkZXJzWz8oQC5uYW1lID1+IC9eKFgtT3JpZ2luYWwtU2VuZGVyfERlbGl2ZXJlZC1Ub3xTZW5kZXJ8SW4tUmVwbHktVG8pJC9pKV0udmFsdWUiCiAgICByZWxhdGl2ZVVybFJlZ2V4OiAiXi9nbWFpbC92MS91c2Vycy8uKj8vbWVzc2FnZXMvLioiCnJlZGFjdGlvbnM6CiAgLSBqc29uUGF0aHM6CiAgICAgIC0gIiQucGF5bG9hZC5oZWFkZXJzWz8oIShALm5hbWUgPX4gL15Gcm9tfFRvfENjfEJjY3xYLU9yaWdpbmFsLVNlbmRlcnxEZWxpdmVyZWQtVG98U2VuZGVyfEluLVJlcGx5LVRvfE1lc3NhZ2UtSUR8RGF0ZXxPcmlnaW5hbC1NZXNzYWdlLUlEfFJlZmVyZW5jZXMkL2kpKV0iCiAgICByZWxhdGl2ZVVybFJlZ2V4OiAiXi9nbWFpbC92MS91c2Vycy8uKj8vbWVzc2FnZXMvLioiCg=="));

        Rules rules = route.getRulesFromConfig().get();
        assertNotNull(rules);
    }
}
