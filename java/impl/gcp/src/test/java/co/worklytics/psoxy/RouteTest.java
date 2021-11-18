package co.worklytics.psoxy;

import co.worklytics.psoxy.rules.Validator;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RouteTest {

    @Test
    void getRulesFromFileSystem() {
        Route route = new Route();

        String path = getClass().getResource("/rules/example.yaml").getPath();


        Optional<Rules> rules = route.getRulesFromFileSystem(Optional.of(path));

        assertTrue(rules.isPresent());
        Validator.validate(rules.get());
    }
}
