package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.ConfigService;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.Endpoint;
import co.worklytics.psoxy.rules.PrebuiltSanitizerRules;
import co.worklytics.psoxy.rules.Rules2;
import com.avaulta.gateway.rules.JsonSchemaFilterUtils;
import com.avaulta.gateway.rules.transforms.EncryptIp;
import com.avaulta.gateway.rules.transforms.HashIp;
import com.avaulta.gateway.rules.transforms.Transform;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.Md5DeterministicTokenizationStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
import java.net.Inet6Address;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static co.worklytics.test.TestModules.withMockEncryptionKey;
import static org.junit.jupiter.api.Assertions.*;

class RESTApiSanitizerImplTest {


    RESTApiSanitizerImpl sanitizer;

    @Inject
    protected RESTApiSanitizerFactory sanitizerFactory;

    @Inject
    protected ReversibleTokenizationStrategy reversibleTokenizationStrategy;

    @Inject
    protected UrlSafeTokenPseudonymEncoder pseudonymEncoder;

    @Inject
    protected PseudonymizerImplFactory pseudonymizerImplFactory;


    @Inject
    ConfigService config;


    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        MockModules.ForConfigService.class,
    })
    public interface Container {
        void inject(RESTApiSanitizerImplTest test);
    }

    @BeforeEach
    public void setup() {
        Container container = DaggerRESTApiSanitizerImplTest_Container.create();
        container.inject(this);

        Pseudonymizer pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
            .pseudonymizationSalt("an irrelevant per org secret")
            .defaultScopeId("scope")
            .pseudonymImplementation(PseudonymImplementation.LEGACY)
            .build());


        sanitizer = sanitizerFactory.create(PrebuiltSanitizerRules.DEFAULTS.get("gmail"), pseudonymizer);

        withMockEncryptionKey(config);
    }

    @SneakyThrows
    @Test
    void sanitize_poc() {

        String jsonPart = "{\n" +
            "        \"name\": \"To\",\n" +
            "        \"value\": \"ops@worklytics.co\"\n" +
            "      }";

        String jsonString = new String(TestUtils.getData("sources/google-workspace/gmail/example-api-responses/original/message.json"));

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains(jsonPart));
        assertTrue(jsonString.contains("alice@worklytics.co"));
        assertTrue(jsonString.contains("Subject"));

        String sanitized = sanitizer.sanitize("GET", new URL("https", "gmail.googleapis.com", "/gmail/v1/users/me/messages/17c3b1911726ef3f\\?format=metadata"), jsonString);


        //email address should disappear
        assertFalse(sanitized.contains(jsonPart));
        assertFalse(sanitized.contains(jsonPart.replaceAll("\\s","")));
        assertFalse(sanitized.contains("alice@worklytics.co"));

        //redaction should remove 'Subject' header entirely; and NOT just replace it with `null`
        assertFalse(sanitized.contains("Subject"));
        assertFalse(sanitized.contains("null"));
    }




    @SneakyThrows
    @ValueSource(strings = {
        "https://gmail.googleapis.com/gmail/v1/users/me/messages/17c3b1911726ef3f?format=metadata",
        "https://gmail.googleapis.com/gmail/v1/users/me/messages",
    })
    @ParameterizedTest
    void allowedEndpointRegex_allowed(String url) {
        assertTrue(sanitizer.isAllowed("GET", new URL(url)));
    }

    @SneakyThrows
    @ValueSource(strings = {
        "https://gmail.googleapis.com/gmail/v1/users/me/threads",
        "https://gmail.googleapis.com/gmail/v1/users/me/profile",
        "https://gmail.googleapis.com/gmail/v1/users/me/settings/forwardingAddresses",
        "https://gmail.googleapis.com/gmail/v1/users/me/somethingPrivate/17c3b1911726ef3f\\?attemptToTrickRegex=messages",
        "https://gmail.googleapis.com/gmail/v1/users/me/should-not-pass?anotherAttempt=https://gmail.googleapis.com/gmail/v1/users/me/messages"
    })
    @ParameterizedTest
    void allowedEndpointRegex_blocked(String url) {
        assertFalse(sanitizer.isAllowed("GET", new URL(url)));
    }

    @SneakyThrows
    @ValueSource(strings = {
        "pwd=1234asAf",
        " pwd=1234asAf  ",
        "https://asdf.google.com/asdf/?pwd=1234asAf",
        "https://asdf.google.com/asdf/?pwd=1234asAf&pwd=14324",
        "https://asdf.google.com/asdf/?asdf=2134&pwd=1234asAf&",
        "https://asdf.google.com/asdf/?asdf=2134&PWD=1234asAf&",
        "https://asdf.google.com/asdf/?asdf=2134&Pwd=1234asAf&"
    })
    @ParameterizedTest
    void redactRegexMatches(String source) {
        Transform.RedactRegexMatches transform = Transform.RedactRegexMatches.builder().redaction("(?i)pwd=[^&]*").build();

        assertTrue(StringUtils.containsIgnoreCase(source, "pwd=1234asAf"));
        String redacted = (String) sanitizer.getRedactRegexMatches(transform).map(source, sanitizer.jsonConfiguration);
        assertFalse(StringUtils.containsIgnoreCase(redacted, "pwd=1234asAf"));
    }


    @SneakyThrows
    @ValueSource(strings = {
        "https://acme.zoom.us/12312345?pwd=1234asAf asdfasdf",
        " https://acme.zoom.us/12312345?pwd=1234asAf  ",
        "come to my zoom meeting! https://acme.zoom.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\n https://acme.zoom.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\nhttps://acme.zoom.us/12312345?pwd=1234asAf\r\n",
        "come to my zoom meeting! \r\n this is the url: https://acme.zoom.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\n this is the url: https://acme.zoom.us/12312345?pwd=1234asAf\r\nthat was the url",
        "https://acme.zoom.us/12312345?pwd=1234asAf"
    })
    @ParameterizedTest
    void filterTokensByRegex(String source) {
        Transform.FilterTokenByRegex transform = Transform.FilterTokenByRegex.builder()
            .filter("https://[^.]+\\.zoom\\.us/.*").build();

        String redacted = (String) sanitizer.getFilterTokenByRegex(transform).map(source, sanitizer.jsonConfiguration);

        assertEquals("https://acme.zoom.us/12312345?pwd=1234asAf", redacted);
    }

    @SneakyThrows
    @ValueSource(strings = {
        "",
        " https://acme.meet.us/12312345?pwd=1234asAf  ",
        "come to my zoom meeting! https://acme.meet.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\n https://acme.meet.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\nhttps://zoom.us/12312345?pwd=1234asAf\r\n",
        "come to my zoom meeting! \r\n this is the url: https://acme.asdfasd.zoom.us/12312345?pwd=1234asAf",
        "come to my zoom meeting! \r\n this is the url: https://acme/zoom.us/12312345?pwd=1234asAf\r\nthat was the url",
        "https://acme/zoom.us/12312345?pwd=1234asAf",
        "  ",
        "\r\n",
    })
    @ParameterizedTest
    void filterTokensByRegex_rejects(String source) {
        Transform.FilterTokenByRegex transform = Transform.FilterTokenByRegex.builder()
            .filter("https://[^.]+\\.zoom\\.us/.*").build();

        String redacted = (String) sanitizer.getFilterTokenByRegex(transform)
            .map(source, sanitizer.jsonConfiguration);

        assertTrue(StringUtils.isBlank(redacted));
    }

    @SneakyThrows
    @ValueSource(strings = {
        "\"https://asdf.google.com/asdf/?asdf=2134&Pwd=1234asAf&\"", //url as JSON string
        "\"J8H8eavweUcd321==\"", //base64-encoded value as a JSON-string
        "{\"uuid\":\"J8H8eavweUcd321==\",\"start_time\":\"2019-08-16T19:00:00Z\"}", //JSON object with nested URL encoded
    })
    @ParameterizedTest
    void preservesRoundTrip(String value) {
        Object document = sanitizer.getJsonConfiguration().jsonProvider().parse(value);
        assertEquals(value,
                sanitizer.getJsonConfiguration().jsonProvider().toJson(document), "value not preserved roundtrip");

        //matches default JSON serialization
        assertEquals(value,
            ((new ObjectMapper()).writer().writeValueAsString(document)));
    }

    @SneakyThrows
    @ValueSource(strings = {
        "{\"uuid\":\"J8H8eavweUcd321==\",\"start_time\":\"2019-08-16T19:00:00Z\"}", //JSON object with nested URL encoded
        "[{\"uuid\":\"J8H8eavweUcd321==\",\"start_time\":\"2019-08-16T19:00:00Z\"}]", //JSON object with nested URL encoded
    })
    @ParameterizedTest
    void preservesRoundTrip_afterNoop(String value) {
        Object document = sanitizer.getJsonConfiguration().jsonProvider().parse(value);

        JsonPath.compile("$..uuid")
                .map(document, (i, c) -> i, sanitizer.getJsonConfiguration());

        assertEquals(value,
            sanitizer.getJsonConfiguration().jsonProvider().toJson(document), "value not preserved roundtrip");

        //matches default JSON serialization
        assertEquals(value,
            ((new ObjectMapper()).writer().writeValueAsString(document)));
    }


    @Test
    void pseudonymizeWithReversalKey() {
        MapFunction f = sanitizer.getPseudonymize(Transform.Pseudonymize.builder().includeReversible(true).build());

        assertEquals("{\"scope\":\"scope\",\"hash\":\"Htt5DmAnE8xaCjfYnLm83_xR8.hhEJE2f_bkFP2yljg\",\"reversible\":\"p~Z7Bnl_VVOwSmfP9kuT0_Ub-5ic4cCVI4wCHArL1hU0MzTTbTCc7BcR53imT1qZgI\"}",
            f.map("asfa", sanitizer.getJsonConfiguration()));
    }

    @Test
    void reversiblePseudonym() {
        MapFunction f = sanitizer.getPseudonymize(Transform.Pseudonymize.builder().includeReversible(true).build());

        String lcase = (String) f.map("erik@engetc.com", sanitizer.getJsonConfiguration());
        String ucaseFirst = (String) f.map("Erik@engetc.com", sanitizer.getJsonConfiguration());

        assertNotEquals(lcase, ucaseFirst);
        //but hashes the same
        assertEquals(lcase.substring(0, 32), ucaseFirst.substring(0, 32));
    }


    @Test
    void tokenize() {
        String original = "blah";
        MapFunction f = sanitizer.getTokenize(Transform.Tokenize.builder().build());
        String r =  (String) f.map(original, sanitizer.getJsonConfiguration());

        assertArrayEquals(reversibleTokenizationStrategy.getReversibleToken(original),
            pseudonymEncoder.decode(r).getReversible());
    }

    @Test
    void tokenize_regex() {
        String path = "v1.0/$metadata#users('48d31887-5fad-4d73-a9f5-3c356e68a038')/calendars('AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAEGAAAiIsqMbYjsT5e-T7KzowPTAAABuC35AAA%3D')/events";
        String host = "https://graph.microsoft.com/";
        MapFunction f = sanitizer.getTokenize(Transform.Tokenize.builder()
                .regex("^https://graph.microsoft.com/(.*)$")
                .build());
        String r = (String) f.map(host+path, sanitizer.getJsonConfiguration());


        assertNotEquals(host + path, r);
        assertEquals(host + path,
            pseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(r, reversibleTokenizationStrategy));
    }


    @SneakyThrows
    @ValueSource(strings = { "GET", "POST", "PUT", "PATCH" })
    @ParameterizedTest
    void allHttpMethodsAllowed(String httpMethod) {
        final URL EXAMPLE_URL = new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages");
        assertTrue(sanitizer.isAllowed(httpMethod, EXAMPLE_URL));
    }

    @SneakyThrows
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @ParameterizedTest
    void httpMethods_onlyGetAllowed(String notGet) {
        final URL EXAMPLE_URL = new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages");
        RESTApiSanitizerImpl strictSanitizer = sanitizerFactory.create(Rules2.builder()
                .endpoint(Endpoint.builder()
                    .allowedMethods(Collections.singleton("GET"))
                    .pathRegex("^/gmail/v1/users/[^/]*/messages[/]?.*?$")
                    .build())
                .build(),
            sanitizer.pseudonymizer
        );

        assertTrue(strictSanitizer.isAllowed("GET", EXAMPLE_URL));
        assertFalse(strictSanitizer.isAllowed(notGet, EXAMPLE_URL));
    }

    @SneakyThrows
    @Test
    public void schema_poc() {
        String jsonString = new String(TestUtils.getData("sources/google-workspace/gmail/example-api-responses/original/message.json"));


        final URL EXAMPLE_URL = new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages");

        String sanitized = sanitizer.sanitize("GET", EXAMPLE_URL, jsonString);

        assertTrue(sanitized.contains("historyId"));

        JsonSchemaFilterUtils.JsonSchemaFilter jsonSchemaFilter = JsonSchemaFilterUtils.JsonSchemaFilter.builder()
            .type("object")
            .properties(Map.<String, JsonSchemaFilterUtils.JsonSchemaFilter>of(
                "id", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                    .type("string")
                    .build(),
                "threadId", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                    .type("string")
                    .build(),
                "labelIds", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                    .type("array")
                    .items(JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                        .type("string")
                        .build())
                    .build(),
                "payload", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                    .type("object")
                    .properties(Map.<String, JsonSchemaFilterUtils.JsonSchemaFilter>of(
                        "headers", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("array")
                            .items(JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                                .type("object")
                                .properties(Map.<String, JsonSchemaFilterUtils.JsonSchemaFilter>of(
                                    "name", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                                        .type("string")
                                        .build(),
                                    "value", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                                        .type("string")
                                        .build()
                                ))
                                .build())
                            .build(),
                        "partId", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                            .type("string")
                            .build()
                    ))
                    .build(),
                "sizeEstimate", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                    .type("integer")
                    .build(),
                "internalDate", JsonSchemaFilterUtils.JsonSchemaFilter.builder()
                    .type("string")
                    .build()
                ))
            .build();

        RESTApiSanitizerImpl strictSanitizer = sanitizerFactory.create(Rules2.builder()
                .endpoint(Endpoint.builder()
                    .allowedMethods(Collections.singleton("GET"))
                    .pathRegex("^/gmail/v1/users/[^/]*/messages[/]?.*?$")
                    .responseSchema(jsonSchemaFilter)
                    .build())
                .build(),
                sanitizer.pseudonymizer);

        String strict = strictSanitizer.sanitize("GET", EXAMPLE_URL, jsonString);

        assertTrue(strict.contains("internalDate"));
        assertFalse(strict.contains("historyId"));
    }

    @ValueSource(strings = {
        "alice@worklytics.co, bob@worklytics.co",
        "\"Alice Example\" <alice@worklytics.co>, \"Bob Example\" <bob@worklytics.co>",
        "Alice.Example@worklytics.co,Bob@worklytics.co",
        // TODO: per RFC 2822, the following SHOULD work ... but indeed lib we're using fails on it
        //"Alice.Example@worklytics.co, , Bob@worklytics.co"
    })
    @ParameterizedTest
    void pseudonymize_multivalueEmailHeaders(String headerValue) {
        List<PseudonymizedIdentity> pseudonyms = sanitizer.pseudonymizeEmailHeader(headerValue);
        assertEquals(2, pseudonyms.size());
        assertTrue(pseudonyms.stream().allMatch(p -> Objects.equals("worklytics.co", p.getDomain())));
    }


    @CsvSource(
        value = {
            "LEGACY,test,false,URL_SAFE_TOKEN,vja8bQGC4pq5kPnJR9D5JFG.WY2S0CX9y5bNT1KmutM",
            "LEGACY,alice@acme.com,false,URL_SAFE_TOKEN,BlFx65qHrkRrhMsuq7lg4bCpwsbXgpLhVZnZ6VBMqoY",
            //legacy w reversible fail to JSON format!!
            "LEGACY,test,true,URL_SAFE_TOKEN,'{\"scope\":\"scope\",\"hash\":\"vja8bQGC4pq5kPnJR9D5JFG.WY2S0CX9y5bNT1KmutM\",\"reversible\":\"p~Tt8H7clbL9y8ryN4_RLYrCEsKqbjJsWcPmKb4wOdZDKAHyevsJLhRTypmrf-DpBZ\"}'",
            "LEGACY,alice@acme.com,true,URL_SAFE_TOKEN,'{\"scope\":\"email\",\"domain\":\"acme.com\",\"hash\":\"BlFx65qHrkRrhMsuq7lg4bCpwsbXgpLhVZnZ6VBMqoY\",\"reversible\":\"p~UFdK0TvVTvZ23c6QslyCy0o2MSq2DRtDjEXfTPJyyMnKYUk8FJevl3wvFyZY0eF-@acme.com\"}'",

            // pseudonyms build with DEFAULT implementation always support URL_SAFE_TOKEN encoding
            "DEFAULT,test,false,URL_SAFE_TOKEN,Tt8H7clbL9y8ryN4_RLYrCEsKqbjJsWcPmKb4wOdZDI",
            "DEFAULT,test,true,URL_SAFE_TOKEN,p~Tt8H7clbL9y8ryN4_RLYrCEsKqbjJsWcPmKb4wOdZDKAHyevsJLhRTypmrf-DpBZ",
            "DEFAULT,alice@acme.com,true,URL_SAFE_TOKEN,p~UFdK0TvVTvZ23c6QslyCy0o2MSq2DRtDjEXfTPJyyMnKYUk8FJevl3wvFyZY0eF-@acme.com",
            "DEFAULT,alice@acme.com,false,URL_SAFE_TOKEN,UFdK0TvVTvZ23c6QslyCy0o2MSq2DRtDjEXfTPJyyMk",
        }
    )
    @ParameterizedTest
    public void getPseudonymize_URL_SAFE_TOKEN(PseudonymImplementation implementation, String value, Boolean includeReversible, String encoding, String expected) {

        Pseudonymizer pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
            .pseudonymizationSalt("an irrelevant per org secret")
            .defaultScopeId("scope")
            .pseudonymImplementation(implementation)
            .build());


        sanitizer = sanitizerFactory.create(PrebuiltSanitizerRules.DEFAULTS.get("gmail"), pseudonymizer);

        String r = (String) sanitizer.getPseudonymize(Transform.Pseudonymize.builder()
                //includeOriginal must be 'false' for URL_SAFE_TOKEN
                .includeReversible(includeReversible)
                .encoding(PseudonymEncoder.Implementations.valueOf(encoding))
                .build()).map(value, sanitizer.getJsonConfiguration());

        assertEquals(expected, r);
    }

    @CsvSource(
        value = {
            "/,^/$",
            "/api/v1/users,^/api/v1/users$",
            "/api/v1/users/{id},^/api/v1/users/(?<id>[^/]+)$",
            "/api/v1/mail/{accountId}/messages/{id},^/api/v1/mail/(?<accountId>[^/]+)/messages/(?<id>[^/]+)$",
            "/enterprise.info,^/enterprise\\.info$",
            "/enterprise$something,^/enterprise\\$something$",
            "/enterprise-info,^/enterprise\\-info$",
            "/enterprise=info,^/enterprise\\=info$",
            "/enterprise+info,^/enterprise\\+info$",
            "/enterprise*info,^/enterprise\\*info$",
        }
    )
    @ParameterizedTest
    void effectiveRegex_templates(String template, String expectedPattern) {
        String effectiveRegex = sanitizer.effectiveRegex(Endpoint.builder().pathTemplate(template).build());
        assertEquals(expectedPattern, effectiveRegex);
    }

    @CsvSource(value = {
        "GET,true",
        "POST,false",
        "PUT,false",
    })
    @ParameterizedTest
    void allowedHttpMethods(String method, Boolean allowed) {
        Endpoint endpoint = Endpoint.builder()
                .allowedMethods(Collections.singleton("GET"))
                .build();

        assertEquals(allowed, sanitizer.allowsHttpMethod(method).test(endpoint));
    }

    @SneakyThrows
    @CsvSource(value = {
        "https://api.example.com/api/v1/users/1,true",
        "https://api.example.com/api/v1/users,false",
        "https://api.example.com/api/v1/users/1?foo=bar,true",
    })
    @ParameterizedTest
    void hasPathTemplateMatchingUrl(String url, Boolean expected) {
        Endpoint endpoint = Endpoint.builder()
            .pathTemplate("/api/v1/users/{id}")
            .build();

        Map.Entry<Endpoint, Pattern> entry = Map.entry(endpoint, Pattern.compile(sanitizer.effectiveRegex(endpoint)));

        assertEquals(expected, sanitizer.getHasPathTemplateMatchingUrl(new URL(url)).test(entry));
    }

    @SneakyThrows
    @CsvSource(value = {
        "https://api.example.com/api/v1/users/1,true",
        "https://api.example.com/api/v1/users,false",
        "https://api.example.com/api/v1/users/1?foo=bar,true",
        "https://api.example.com/api/v1/users/1?foo=bar&bar=foo,false",
        "https://api.example.com/api/v1/users/1?bar=foo,false",
    })
    @ParameterizedTest
    void hasPathTemplateMatchingUrl_queryParams(String url, Boolean expected) {
        Endpoint endpoint = Endpoint.builder()
            .pathTemplate("/api/v1/users/{id}")
            .allowedQueryParams(List.of("foo"))
            .build();

        Map.Entry<Endpoint, Pattern> entry = Map.entry(endpoint, Pattern.compile(sanitizer.effectiveRegex(endpoint)));

        assertEquals(expected, sanitizer.getHasPathTemplateMatchingUrl(new URL(url)).test(entry));
    }


    @SneakyThrows
    @CsvSource(value = {
        "something",
    })
    @ParameterizedTest
    void pseudonymizeWithRegexMatches(String input) {

        Pseudonymizer pseudonymizer = pseudonymizerImplFactory.create(Pseudonymizer.ConfigurationOptions.builder()
            .pseudonymizationSalt("an irrelevant per org secret")
            .defaultScopeId("scope")
            .pseudonymImplementation(PseudonymImplementation.DEFAULT)
            .build());


        sanitizer = sanitizerFactory.create(PrebuiltSanitizerRules.DEFAULTS.get("gmail"), pseudonymizer);

        List<MapFunction> transforms = Arrays.asList(
            sanitizer.getPseudonymizeRegexMatches(Transform.PseudonymizeRegexMatches.builder()
                .regex(".*")
                .includeReversible(true)
                .build()),
            sanitizer.getPseudonymizeRegexMatches(Transform.PseudonymizeRegexMatches.builder()
                .regex(".*")
                .includeReversible(false)
                .build()),
            sanitizer.getPseudonymize(Transform.Pseudonymize.builder()
                .includeReversible(true)
                .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                .build()),
            sanitizer.getPseudonymize(Transform.Pseudonymize.builder()
                .includeReversible(false)
                .encoding(PseudonymEncoder.Implementations.URL_SAFE_TOKEN)
                .build())
        );

        List<String> pseudonymized = transforms.stream()
            .map(f -> (String) f.map(input, sanitizer.getJsonConfiguration()))
            .collect(Collectors.toList());

        UrlSafeTokenPseudonymEncoder encoder = new UrlSafeTokenPseudonymEncoder();

        List<Pseudonym> pseudonyms = pseudonymized.stream()
            .map(s -> {
                try {
                    return encoder.decode(s);
                } catch (Exception e) {
                    return encoder.decode(StringUtils.replaceChars(s, ".+/", "--_"));
                }
            })
            .collect(Collectors.toList());

        List<String> hashes = pseudonyms.stream()
            .map(Pseudonym::getHash)
            .map(Base64.getEncoder()::encode)
            .map(String::new)
            .collect(Collectors.toList());

        assertTrue(hashes.stream().allMatch(p -> Objects.equals(hashes.get(0), p)));

    }



    @CsvSource(value = {
        "something,.*,t~wUW4bkpTjD6c.BIQaE_oLQxN4nD5vbnf5HBVz7gxck8",
        "something,blah:.*thing,", // no match, should redact
        "blah:something,blah:.*thing,t~iWybsRb4SscO_Z6v2gpnMTjPujG2E4FtxgYkjWc5HXQ",
        "blah:something,blah:(.*),blah:t~wUW4bkpTjD6c.BIQaE_oLQxN4nD5vbnf5HBVz7gxck8",
    })
    @ParameterizedTest
    public void pseudonymizeWithRegexMatches_nonMatchingRedacted(String input, String regex, String expected) {
        MapFunction transform = sanitizer.getPseudonymizeRegexMatches(Transform.PseudonymizeRegexMatches.builder()
            .regex(regex)
            .includeReversible(false)
            .build());

        assertEquals(StringUtils.trimToNull(expected),
            transform.map(input, sanitizer.getJsonConfiguration()));
    }

    @CsvSource(value = {
        "123.234.252.12,c16e:9376:40a8:d00c:9681:6bf0:b8f:19f3",
        "10.0.0.1,240d:2561:790d:b861:19d:a797:d452:e284",
        ",",
        "not an ip," // redact
    })
    @ParameterizedTest
    public void hashIp(String input, String expected) {
        MapFunction transform = sanitizer.getHashIp(HashIp.builder().build());

        assertEquals(StringUtils.trimToNull(expected),
            transform.map(input, sanitizer.getJsonConfiguration()));
    }

    @SneakyThrows
    @CsvSource(value = {
        "123.234.252.12,e16~wW6TdkCo0AyWgWvwC48Z88ICwFaKIY3roGDJ2lIxURk",
        "10.0.0.1,e16~JA0lYXkNuGEBnaeX1FLihKXznxklfgEYWKWzFUcFLic",
        ",",
        "not an ip," // redact
    })
    @ParameterizedTest
    public void encryptIp(String input, String expected) {
        MapFunction transform = sanitizer.getEncryptIp(EncryptIp.builder().build());


        String encrypted = (String) transform.map(input, sanitizer.getJsonConfiguration());


        assertEquals(StringUtils.trimToNull(expected),
            encrypted);


        if (StringUtils.isNotBlank(expected)) {
            MapFunction hashTransform = sanitizer.getHashIp(HashIp.builder().build());
            String formattedHash = (String) hashTransform.map(input, sanitizer.getJsonConfiguration());

            byte[] hash = Inet6Address.getByName(formattedHash).getAddress();

            Base64.Encoder base64encoder = Base64.getUrlEncoder().withoutPadding();
            String withoutPrefix = encrypted.substring(UrlSafeTokenPseudonymEncoder.ENCRYPTED_MD5_IV_PREFIX.length());
            byte[] decoded = Base64.getUrlDecoder().decode(withoutPrefix.getBytes());


            assertEquals(
                base64encoder.encodeToString(hash),
                base64encoder.encodeToString(Arrays.copyOfRange(decoded, 0, Md5DeterministicTokenizationStrategy.HASH_LENGTH_BYTES))
            );

            UrlSafeTokenPseudonymEncoder encoder = new UrlSafeTokenPseudonymEncoder();
            assertTrue(encoder.canBeDecoded(encrypted));
            Pseudonym decodedPseudonym = encoder.decode(encrypted);
            assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(decodedPseudonym.getHash()),
            Base64.getUrlEncoder().withoutPadding().encodeToString(hash));
        }
    }

}
