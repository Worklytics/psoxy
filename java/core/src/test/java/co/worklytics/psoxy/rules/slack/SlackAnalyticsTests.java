package co.worklytics.psoxy.rules.slack;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@Getter
public class SlackAnalyticsTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.SLACK_ANALYTICS;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("slack")
        .sourceKind("slack")
        .rulesFile("slack-analytics/slack-analytics")
        .exampleApiResponsesDirectoryPath("slack-analytics/example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("slack-analytics/example-api-responses/sanitized/")
        .checkUncompressedSSMLength(false)
        .build();

    @SneakyThrows
    @ValueSource(strings = {
        "https://slack.com/api/admin.analytics.getFile",
    })
    @ParameterizedTest
    void allowedEndpointRegex_allowed(String url) {
        assertTrue(sanitizer.isAllowed("GET", new URL(url)), url + " should be allowed");
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://slack.com/api/admin.analytics.getFile?type=member&date=2024-04-07", "member_sample.json")
        );
    }
}
