package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.PseudonymizedIdentity;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.PseudonymizerImplFactory;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.rules.PrebuiltSanitizerRules;
import co.worklytics.test.MockModules;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.transforms.Transform;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.MapFunction;
import dagger.Component;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class SanitizerUtilsTest {

    @Singleton
    @Component(
        modules = {
            PsoxyModule.class,
            RESTApiSanitizerImplTest.ForConfigService.class,
            MockModules.ForSecretStore.class,
            // TestModules.ForSecretStore.class,
        })
    public interface Container {
        void inject(SanitizerUtilsTest test);
    }

    @Inject
    protected PseudonymizerImplFactory pseudonymizerImplFactory;
    @Inject
    Configuration jsonConfiguration;
    @Inject
    ReversibleTokenizationStrategy reversibleTokenizationStrategy;
    @Inject
    UrlSafeTokenPseudonymEncoder urlSafePseudonymEncoder;
    @Inject
    SanitizerUtils sanitizerUtils;


    Pseudonymizer pseudonymizer;

    @BeforeEach
    public void setup() {
        SanitizerUtilsTest.Container container = DaggerSanitizerUtilsTest_Container.create();
        container.inject(this);

       pseudonymizer = pseudonymizerImplFactory
           .create(Pseudonymizer.ConfigurationOptions.builder().build());

    }

    @SneakyThrows
    @ValueSource(strings = {"pwd=1234asAf", " pwd=1234asAf  ",
        "https://asdf.google.com/asdf/?pwd=1234asAf",
        "https://asdf.google.com/asdf/?pwd=1234asAf&pwd=14324",
        "https://asdf.google.com/asdf/?asdf=2134&pwd=1234asAf&",
        "https://asdf.google.com/asdf/?asdf=2134&PWD=1234asAf&",
        "https://asdf.google.com/asdf/?asdf=2134&Pwd=1234asAf&"})
    @ParameterizedTest
    void redactRegexMatches(String source) {
        Transform.RedactRegexMatches transform =
            Transform.RedactRegexMatches.builder().redaction("(?i)pwd=[^&]*").build();

        assertTrue(StringUtils.containsIgnoreCase(source, "pwd=1234asAf"));
        String redacted = (String) sanitizerUtils.getRedactRegexMatches(transform)
            .map(source, jsonConfiguration);
        assertFalse(StringUtils.containsIgnoreCase(redacted, "pwd=1234asAf"));
    }

    @SneakyThrows
    @CsvSource({"phrase1,phrase1", "Phrase1,Phrase1", "phrase2 2,phrase2 2", "phrase3,phrase3",
        "phrase4,", "blah phrase1,phrase1", "blah phrase1: blah,phrase1",})
    @ParameterizedTest
    void redactExceptPhases(String raw, String sanitized) {
        Transform.RedactExceptPhrases transform = Transform.RedactExceptPhrases.builder()
            .allowedPhrases(Arrays.asList("phrase1", "phrase2 2", "Phrase3")).build();

        String redacted = (String) sanitizerUtils.getRedactExceptPhrases(transform)
            .map(raw, jsonConfiguration);

        assertEquals(sanitized, StringUtils.trimToNull(redacted));
    }

    @SneakyThrows
    @ValueSource(strings = {"https://acme.zoom.us/12312345?pwd=1234asAf asdfasdf",
        " https://acme.zoom.us/12312345?pwd=1234asAf  ",
        "come to my zoom meeting! https://acme.zoom.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\n https://acme.zoom.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\nhttps://acme.zoom.us/12312345?pwd=1234asAf\r\n",
        "come to my zoom meeting! \r\n this is the url: https://acme.zoom.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\n this is the url: https://acme.zoom.us/12312345?pwd=1234asAf\r\nthat was the url",
        "https://acme.zoom.us/12312345?pwd=1234asAf"})
    @ParameterizedTest
    void filterTokensByRegex(String source) {
        Transform.FilterTokenByRegex transform = Transform.FilterTokenByRegex.builder()
            .filter("https://[^.]+\\.zoom\\.us/.*").build();

        String redacted = (String) sanitizerUtils.getFilterTokenByRegex(transform)
            .map(source, jsonConfiguration);

        assertEquals("https://acme.zoom.us/12312345?pwd=1234asAf", redacted);
    }

    @SneakyThrows
    @ValueSource(strings = {"", " https://acme.meet.us/12312345?pwd=1234asAf  ",
        "come to my zoom meeting! https://acme.meet.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\n https://acme.meet.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\nhttps://zoom.us/12312345?pwd=1234asAf\r\n",
        "come to my zoom meeting! \r\n this is the url: https://acme.asdfasd.zoom.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\n this is the url: https://acme/zoom.us/12312345?pwd=1234asAf\r\nthat was the url",
        "https://acme/zoom.us/12312345?pwd=1234asAf", "  ", "\r\n",})
    @ParameterizedTest
    void filterTokensByRegex_rejects(String source) {
        Transform.FilterTokenByRegex transform = Transform.FilterTokenByRegex.builder()
            .filter("https://[^.]+\\.zoom\\.us/.*").build();

        String redacted = (String) sanitizerUtils.getFilterTokenByRegex(transform)
            .map(source, jsonConfiguration);

        assertTrue(StringUtils.isBlank(redacted));
    }


    @Test
    void pseudonymizeWithReversalKey() {
        // NOTE: this is a LEGACY case
        MapFunction f = sanitizerUtils.getPseudonymize(pseudonymizer,
            Transform.Pseudonymize.builder().includeReversible(true).build());

        assertEquals(
            "p~Z7Bnl_VVOwSmfP9kuT0_Ub-5ic4cCVI4wCHArL1hU0MzTTbTCc7BcR53imT1qZgI",
            f.map("asfa", jsonConfiguration));
    }

    @Test
    void pseudonymizeWithJsonEscaped() {
        String input = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"mention\",\"attrs\":{\"id\":\"608a9b555426330072f9867d\",\"text\":\"@alice\"}},{\"text\":\" this is a reply from a reply?\",\"type\":\"text\"}]}],\"version\":1}";
        String expected = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"mention\",\"attrs\":{\"id\":\"t~7Z5-G-SoDUxVDxHtoJZFqVZ-ri3XiGo3ylaDNUVtY6Q\",\"text\":\"@alice\"}},{\"text\":\" this is a reply from a reply?\",\"type\":\"text\"}]}],\"version\":1}";
        MapFunction f = sanitizerUtils.getPseudonymize(pseudonymizer,
            Transform.Pseudonymize.builder()
                .isJsonEscaped(true)
                .jsonPathToProcessWhenEscaped("$..attrs.id")
                .build());

        assertEquals(expected, f.map(input, jsonConfiguration));
    }

    @Test
    void reversiblePseudonym() {
        MapFunction f = sanitizerUtils.getPseudonymize(pseudonymizer,
            Transform.Pseudonymize.builder().includeReversible(true).build());

        String lcase = (String) f.map("erik@engetc.com", jsonConfiguration);
        String ucaseFirst = (String) f.map("Erik@engetc.com", jsonConfiguration);

        assertNotEquals(lcase, ucaseFirst);
        // but hashes the same
        assertEquals(lcase.substring(0, 32), ucaseFirst.substring(0, 32));
    }


    @Test
    void tokenize() {
        String original = "blah";
        MapFunction f = sanitizerUtils.getTokenize(Transform.Tokenize.builder().build());
        String r = (String) f.map(original, jsonConfiguration);

        assertArrayEquals(reversibleTokenizationStrategy.getReversibleToken(original),
            urlSafePseudonymEncoder.decode(r).getReversible());
    }

    @Test
    void tokenize_regex_on_the_middle() {
        String path =
            "/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendars('AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAEGAAAiIsqMbYjsT5e-T7KzowPTAAABuC35AAA%3D')/events";
        String host = "https://graph.microsoft.com";
        MapFunction f = sanitizerUtils.getTokenize(Transform.Tokenize.builder()
            .regex("^https://graph.microsoft.com/v1.0/users/([a-zA-Z0-9_-]+)/.*$").build());
        String r = (String) f.map(host + path, jsonConfiguration);

        assertNotEquals(host + path, r);
        assertEquals(host + path, urlSafePseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(r,
            reversibleTokenizationStrategy));
    }


    @SneakyThrows
    @ParameterizedTest
    @CsvSource({"'Hello world', 2, 11", "'This is a test', 4, 14", "'', 0, 0", "'OneWord', 1, 7"})
    void textDigest(String input, int expectedWordCount, int expectedLength) {
        Transform.TextDigest transform = Transform.TextDigest.builder().build();
        MapFunction textDigestFunction = sanitizerUtils.getTextDigest(transform);

        String resultJson =
            (String) textDigestFunction.map(input, jsonConfiguration);
        Map<String, Integer> result = new ObjectMapper().readValue(resultJson, Map.class);

        assertEquals(expectedWordCount, result.get("word_count"));
        assertEquals(expectedLength, result.get("length"));
    }

    @SneakyThrows
    @Test
    void textDigest_with_escaping() {
        String input = "{\n" + "  \"type\": \"AdaptiveCard\",\n" + "  \"version\": \"1.0\",\n"
            + "  \"body\": [\n" + "    {\n" + "      \"type\": \"TextBlock\",\n"
            + "      \"text\": \"It looks like there were no important \\\"emails\\\" from last week. However, I found some relevant meetings and files that might be of interest to you.\\n\\nFrom your meetings last week:\\n- **[test meeting2 - export api](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI6AAA%3d)**: You discussed the need to send a reminder about an upcoming event, possibly Ignite, scheduled for next week. You emphasized the importance of the event and the reminder[1](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI6AAA%3d).\\n- **[new meeting to test copilot interaction in meetings](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI5AAA%3d)**: This meeting was held last Friday from 12:30 PM to 1 PM[2](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI5AAA%3d).\\n- **[teste meeting](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABAvsP6AAA%3d)**: You explained the significance of the Nobel Prize in Economics and announced the 2024 Nobel Prize winners, Darren Simon Johnson and James A. Robinson[3](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABAvsP6AAA%3d).\\n\\nAdditionally, there is a file titled **[OnCall DRI Handbook-v3](https://m365cpi17278319-my.sharepoint.com/personal/corat_m365cpi17278319_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/OnCall%20DRI%20Handbook-v3.pdf?web=1)** that you last modified on February 4th, 2021. This document provides guidelines on handling incidents and includes important terminology and procedures[4](https://m365cpi17278319-my.sharepoint.com/personal/corat_m365cpi17278319_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/OnCall%20DRI%20Handbook-v3.pdf?web=1).\\n\\nIs there anything specific you would like to know more about?\",\n"
            + "      \"wrap\": true\n" + "    },\n" + "    {\n"
            + "      \"type\": \"TextBlock\",\n" + "      \"id\": \"MessageTextField\",\n"
            + "      \"text\": \"It looks like there were no important \\\"emails\\\" from last week. However, I found some relevant meetings and files that might be of interest to you.\\n\\nFrom your meetings last week:\\n- **test meeting2 - export api[3]**: You discussed the need to send a reminder about an upcoming event, possibly Ignite, scheduled for next week. You emphasized the importance of the event and the reminder[^2^].\\n- **new meeting to test copilot interaction in meetings[3]**: This meeting was held last Friday from 12:30 PM to 1 PM[^3^].\\n- **teste meeting[3]**: You explained the significance of the Nobel Prize in Economics and announced the 2024 Nobel Prize winners, Darren Simon Johnson and James A. Robinson[^4^].\\n\\nAdditionally, there is a file titled **OnCall DRI Handbook-v3[2]** that you last modified on February 4th, 2021. This document provides guidelines on handling incidents and includes important terminology and procedures[^1^].\\n\\nIs there anything specific you would like to know more about?\",\n"
            + "      \"wrap\": true\n" + "    }\n" + "  ]\n" + "}";

        String expected =
            "{\"type\":\"AdaptiveCard\",\"version\":\"1.0\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"{\\\"length\\\":982,\\\"word_count\\\":154}\",\"wrap\":true},{\"type\":\"TextBlock\",\"id\":\"MessageTextField\",\"text\":\"{\\\"length\\\":982,\\\"word_count\\\":154}\",\"wrap\":true}]}";

        Transform.TextDigest transform = Transform.TextDigest.builder().isJsonEscaped(true)
            .jsonPathToProcessWhenEscaped("$..text").build();
        MapFunction textDigestFunction = sanitizerUtils.getTextDigest(transform);

        String resultJson =
            (String) textDigestFunction.map(input, jsonConfiguration);

        assertEquals(expected, resultJson);
    }

    @SneakyThrows
    @Test
    void textDigest_with_escaping_should_keep_input_if_not_matching() {
        String input = "{\n" + "  \"type\": \"AdaptiveCard\",\n" + "  \"version\": \"1.0\",\n"
            + "  \"body\": [\n" + "    {\n" + "      \"type\": \"TextBlock\",\n"
            + "      \"text\": \"It looks like there were no important \\\"emails\\\" from last week. However, I found some relevant meetings and files that might be of interest to you.\\n\\nFrom your meetings last week:\\n- **[test meeting2 - export api](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI6AAA%3d)**: You discussed the need to send a reminder about an upcoming event, possibly Ignite, scheduled for next week. You emphasized the importance of the event and the reminder[1](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI6AAA%3d).\\n- **[new meeting to test copilot interaction in meetings](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI5AAA%3d)**: This meeting was held last Friday from 12:30 PM to 1 PM[2](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI5AAA%3d).\\n- **[teste meeting](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABAvsP6AAA%3d)**: You explained the significance of the Nobel Prize in Economics and announced the 2024 Nobel Prize winners, Darren Simon Johnson and James A. Robinson[3](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABAvsP6AAA%3d).\\n\\nAdditionally, there is a file titled **[OnCall DRI Handbook-v3](https://m365cpi17278319-my.sharepoint.com/personal/corat_m365cpi17278319_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/OnCall%20DRI%20Handbook-v3.pdf?web=1)** that you last modified on February 4th, 2021. This document provides guidelines on handling incidents and includes important terminology and procedures[4](https://m365cpi17278319-my.sharepoint.com/personal/corat_m365cpi17278319_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/OnCall%20DRI%20Handbook-v3.pdf?web=1).\\n\\nIs there anything specific you would like to know more about?\",\n"
            + "      \"wrap\": true\n" + "    },\n" + "    {\n"
            + "      \"type\": \"TextBlock\",\n" + "      \"id\": \"MessageTextField\",\n"
            + "      \"text\": \"It looks like there were no important \\\"emails\\\" from last week. However, I found some relevant meetings and files that might be of interest to you.\\n\\nFrom your meetings last week:\\n- **test meeting2 - export api[3]**: You discussed the need to send a reminder about an upcoming event, possibly Ignite, scheduled for next week. You emphasized the importance of the event and the reminder[^2^].\\n- **new meeting to test copilot interaction in meetings[3]**: This meeting was held last Friday from 12:30 PM to 1 PM[^3^].\\n- **teste meeting[3]**: You explained the significance of the Nobel Prize in Economics and announced the 2024 Nobel Prize winners, Darren Simon Johnson and James A. Robinson[^4^].\\n\\nAdditionally, there is a file titled **OnCall DRI Handbook-v3[2]** that you last modified on February 4th, 2021. This document provides guidelines on handling incidents and includes important terminology and procedures[^1^].\\n\\nIs there anything specific you would like to know more about?\",\n"
            + "      \"wrap\": true\n" + "    }\n" + "  ]\n" + "}";

        String expected =
            "{\"type\":\"AdaptiveCard\",\"version\":\"1.0\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"It looks like there were no important \\\"emails\\\" from last week. However, I found some relevant meetings and files that might be of interest to you.\\n\\nFrom your meetings last week:\\n- **[test meeting2 - export api](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI6AAA%3d)**: You discussed the need to send a reminder about an upcoming event, possibly Ignite, scheduled for next week. You emphasized the importance of the event and the reminder[1](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI6AAA%3d).\\n- **[new meeting to test copilot interaction in meetings](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI5AAA%3d)**: This meeting was held last Friday from 12:30 PM to 1 PM[2](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABCrxI5AAA%3d).\\n- **[teste meeting](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABAvsP6AAA%3d)**: You explained the significance of the Nobel Prize in Economics and announced the 2024 Nobel Prize winners, Darren Simon Johnson and James A. Robinson[3](https://teams.microsoft.com/l/meeting/details?eventId=AAMkADcyZTMzNWZhLWE1YjAtNDc3Mi04MzBlLTc2NzEzOTE0MmU1ZQBGAAAAAAC5e4DRHIMCQJ-tS6nB82CZBwCMIOyf3WTwTIsBMwZamp77AAAAAAENAACMIOyf3WTwTIsBMwZamp77AABAvsP6AAA%3d).\\n\\nAdditionally, there is a file titled **[OnCall DRI Handbook-v3](https://m365cpi17278319-my.sharepoint.com/personal/corat_m365cpi17278319_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/OnCall%20DRI%20Handbook-v3.pdf?web=1)** that you last modified on February 4th, 2021. This document provides guidelines on handling incidents and includes important terminology and procedures[4](https://m365cpi17278319-my.sharepoint.com/personal/corat_m365cpi17278319_onmicrosoft_com/Documents/Microsoft%20Copilot%20Chat%20Files/OnCall%20DRI%20Handbook-v3.pdf?web=1).\\n\\nIs there anything specific you would like to know more about?\",\"wrap\":true},{\"type\":\"TextBlock\",\"id\":\"MessageTextField\",\"text\":\"It looks like there were no important \\\"emails\\\" from last week. However, I found some relevant meetings and files that might be of interest to you.\\n\\nFrom your meetings last week:\\n- **test meeting2 - export api[3]**: You discussed the need to send a reminder about an upcoming event, possibly Ignite, scheduled for next week. You emphasized the importance of the event and the reminder[^2^].\\n- **new meeting to test copilot interaction in meetings[3]**: This meeting was held last Friday from 12:30 PM to 1 PM[^3^].\\n- **teste meeting[3]**: You explained the significance of the Nobel Prize in Economics and announced the 2024 Nobel Prize winners, Darren Simon Johnson and James A. Robinson[^4^].\\n\\nAdditionally, there is a file titled **OnCall DRI Handbook-v3[2]** that you last modified on February 4th, 2021. This document provides guidelines on handling incidents and includes important terminology and procedures[^1^].\\n\\nIs there anything specific you would like to know more about?\",\"wrap\":true}]}";

        Transform.TextDigest transform = Transform.TextDigest.builder().isJsonEscaped(true)
            .jsonPathToProcessWhenEscaped("$..notFound").build();
        MapFunction textDigestFunction = sanitizerUtils.getTextDigest(transform);

        String resultJson =
            (String) textDigestFunction.map(input, jsonConfiguration);

        assertEquals(expected, resultJson);
    }

    @SneakyThrows
    @ValueSource(strings = {"alice@worklytics.co, bob@worklytics.co",
        "\"Alice Example\" <alice@worklytics.co>, \"Bob Example\" <bob@worklytics.co>",
        "Alice.Example@worklytics.co,Bob@worklytics.co",
        // TODO: per RFC 2822, the following SHOULD work ... but indeed lib we're using fails on it
        // "Alice.Example@worklytics.co, , Bob@worklytics.co"
    })
    @ParameterizedTest
    void pseudonymize_multivalueEmailHeaders(String headerValue) {

        MapFunction mapFunction =
            sanitizerUtils.getPseudonymizeEmailHeaderToJson(pseudonymizer,
                Transform.PseudonymizeEmailHeader.builder().encoding(PseudonymEncoder.Implementations.JSON).build());

        String asJson = (String) mapFunction.map(headerValue, jsonConfiguration);

        List<PseudonymizedIdentity> pseudonyms = sanitizerUtils
            .pseudonymizeEmailHeader(pseudonymizer, headerValue);


        assertEquals(2, pseudonyms.size());
        assertTrue(
            pseudonyms.stream().allMatch(p -> Objects.equals("worklytics.co", p.getDomain())));
    }
}
