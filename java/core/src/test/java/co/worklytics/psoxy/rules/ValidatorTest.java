package co.worklytics.psoxy.rules;

import org.junit.jupiter.api.Test;

import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {

    @Test
    void allowedEndpointRegex() {

        Rules1 shouldPass = Rules1.builder()
            .allowedEndpointRegex("/admin/reports/v1/activity/users/all/applications/chat.*")
            .build();

        Validator.validate(shouldPass);

        Rules1 shouldFail = Rules1.builder()
            .allowedEndpointRegex("[") //not actually that easy to write an invalid pattern ...
            .build();

        try {
            Validator.validate(shouldFail);
            fail("Validator should have thrown exception");
        } catch (PatternSyntaxException e) {
            //expectedgit
        }

    }

}
