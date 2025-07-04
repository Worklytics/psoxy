package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.RESTApiSanitizer;
import co.worklytics.psoxy.gateway.ApiModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.utils.URLUtils;
import co.worklytics.psoxy.utils.email.EmailAddressParser;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.JsonSchemaFilterUtils;
import com.avaulta.gateway.rules.ParameterSchemaUtils;
import com.avaulta.gateway.rules.PathTemplateUtils;
import com.avaulta.gateway.rules.transforms.Transform;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

@Log
public class RESTApiSanitizerImpl implements RESTApiSanitizer {

    @Getter
    final RESTRules rules;
    @Getter
    final Pseudonymizer pseudonymizer;
    final EmailAddressParser emailAddressParser;


    //NOTE: JsonPath seems to be threadsafe
    //  - https://github.com/json-path/JsonPath/issues/384
    //  - https://github.com/json-path/JsonPath/issues/187 (earlier issue fixing stuff that wasn't thread-safe)

    Map<Endpoint, Pattern> compiledAllowedEndpoints;

    private final Object $writeLock = new Object[0];
    Map<Transform, List<JsonPath>> compiledTransforms = new ConcurrentHashMap<>();

    JsonSchemaFilter rootDefinitions;

    String targetHostPath;

    @AssistedInject
    public RESTApiSanitizerImpl(@Assisted RESTRules rules,
                                @Assisted Pseudonymizer pseudonymizer,
                                EmailAddressParser emailAddressParser) {
        this.rules = rules;
        this.pseudonymizer = pseudonymizer;
        this.emailAddressParser = emailAddressParser;
    }

    @Getter(onMethod_ = {@VisibleForTesting})
    @Inject
    Configuration jsonConfiguration;

    @Inject
    ReversibleTokenizationStrategy reversibleTokenizationStrategy;
    @Inject
    UrlSafeTokenPseudonymEncoder urlSafePseudonymEncoder;
    @Inject
    JsonSchemaFilterUtils jsonSchemaFilterUtils;
    @Inject
    ParameterSchemaUtils parameterSchemaUtils;
    @Inject
    PathTemplateUtils pathTemplateUtils;
    @Inject
    ConfigService configService;
    @Inject
    SanitizerUtils sanitizerUtils;

    @Inject
    @Named("ipEncryptionStrategy")
    ReversibleTokenizationStrategy ipEncryptStrategy;
    @Inject
    @Named("ipHashStrategy")
    DeterministicTokenizationStrategy ipHashStrategy;

    @Inject
    ObjectMapper objectMapper;


    @Override
    public boolean isAllowed(@NonNull String httpMethod, @NonNull URL url) {
        return rules.getAllowAllEndpoints() || getEndpoint(httpMethod, url).isPresent();
    }

    @Override
    public Optional<Collection<String>> getAllowedHeadersToForward(String httpMethod, URL url) {
        return getEndpoint(httpMethod, url)
            .map(Pair::getRight)
            .map(endpoint -> endpoint.getAllowedRequestHeadersToForward())
            .orElse(Optional.empty());
    }

    @Override
    public String sanitize(String httpMethod, URL url, String jsonResponse) {
        if (StringUtils.isEmpty(jsonResponse)) {
            // Nothing to do
            return jsonResponse;
        }
        // Convert input String to InputStream (UTF-8 encoding)
        try (InputStream input = new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8));
             InputStream sanitizedStream = sanitize(httpMethod, url, input)) {

            // Read all bytes from the sanitized stream
            byte[] sanitizedBytes = sanitizedStream.readAllBytes();
            return new String(sanitizedBytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Wrap IOException in unchecked exception, or handle as needed
            throw new UncheckedIOException("Failed to sanitize content", e);
        }
    }

    @Override
    public InputStream sanitize(String httpMethod, URL url, InputStream response) throws IOException {
        //extra check ...
        if (!isAllowed(httpMethod, url)) {
            throw new IllegalStateException(String.format("Sanitizer called to sanitize response that should not have been retrieved: %s", url));
        }
        Optional<Pair<Pattern, Endpoint>> matchingEndpoint = getEndpoint(httpMethod, url);

        if (matchingEndpoint.isEmpty()) {
            // No matching endpoint found, return the original response
            return response;
        }

        //q: overkill for NON-ndjson case?

        // Create piped stream pair
        PipedOutputStream outPipe = new PipedOutputStream();
        PipedInputStream inPipe = new PipedInputStream(outPipe);

        Endpoint endpoint = matchingEndpoint.get().getValue();
        new Thread(() -> {
            JsonFactory factory = objectMapper.getFactory();

            try (JsonParser parser = factory.createParser(response);
                 OutputStreamWriter writer = new OutputStreamWriter(outPipe, StandardCharsets.UTF_8);
                 BufferedWriter bufferedWriter = new BufferedWriter(writer)) {

                boolean first = true;

                while (parser.nextToken() != null) {
                    // in theory, should parse only the 'next' JSON object from the stream, such that NDJSON, or even "{}{}" works
                    // string --> jackson jsonNode --> string -> jayway object  for compatibility with jayway jsonpath
                    // despite jackson provider underneath, Jayway JSONPath seems not do deal with native jackson JsonNode
                    // TODO: figure out a way to streamline this parsing
                    Object node = jsonConfiguration.jsonProvider().parse(
                        objectMapper.writeValueAsString(objectMapper.readTree(parser)));

                    Object sanitized = sanitize(endpoint, node);
                    if (!first) bufferedWriter.write("\n");
                    bufferedWriter.write(jsonConfiguration.jsonProvider().toJson(sanitized));
                    first = false;
                }
                bufferedWriter.flush();
            } catch (IOException e) {
                // Propagate error by closing the pipe
                try {
                    outPipe.close();
                } catch (IOException ignore) {}
                throw new UncheckedIOException(e);
            } finally {
                try {
                    outPipe.close();
                } catch (IOException ignore) {}
            }
        }).start();

        return inPipe;
    }

    /**
     * sanitized a response JSON by applying the endpoint's response schema and transforms
     *
     * if endpoint allows for ndjson, we expect this to be just a single JSON object at a time, and you call this once per row in the response
     */
    private Object sanitize(Endpoint endpoint, Object jsonResponse) {
        Object document = endpoint.getResponseSchemaOptional()
                .map(schema -> {
                    //q: this read
                    try {
                        //TODO: jayway jsonpath Object --> String --> jayway jsonpath Object here is needlessly inefficient
                        String json = jsonConfiguration.jsonProvider().toJson(jsonResponse);
                        return jsonConfiguration.jsonProvider().parse(jsonSchemaFilterUtils.filterJsonBySchema(json, schema, getRootDefinitions()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(jsonResponse);

            if (ObjectUtils.isNotEmpty(endpoint.getTransforms())) {
                for (Transform transform : endpoint.getTransforms()) {
                    sanitizerUtils.applyTransform(getPseudonymizer(), transform, document, this.compiledTransforms);
                }
            }
            return document;
    }


    Map<Endpoint, Pattern> getCompiledAllowedEndpoints() {
        if (compiledAllowedEndpoints == null) {
            synchronized ($writeLock) {
                if (compiledAllowedEndpoints == null) {
                    compiledAllowedEndpoints = rules.getEndpoints().stream()
                        .collect(Collectors.toMap(Function.identity(),
                            endpoint -> Pattern.compile(effectiveRegex(endpoint), CASE_INSENSITIVE)));
                }
            }
        }
        return compiledAllowedEndpoints;
    }

    @VisibleForTesting
    String effectiveRegex(Endpoint endpoint) {
        return Optional.ofNullable(endpoint.getPathRegex())
            .orElseGet(() -> pathTemplateUtils.asRegex(endpoint.getPathTemplate()));
    }

    boolean allowedQueryParams(Endpoint endpoint, List<Pair<String, String>> queryParams) {
        boolean matchesAllowed = endpoint.getAllowedQueryParamsOptional()
            .map(allowedParams -> allowedParams.containsAll(queryParams.stream().map(Pair::getKey).collect(Collectors.toList())))
            .orElse(true);
        return matchesAllowed
            && endpoint.getQueryParamSchemasOptional()
            .map(schemas -> parameterSchemaUtils.validateAll(schemas, queryParams))
            .orElse(true);
    }

    JsonSchemaFilter getRootDefinitions() {
        if (rootDefinitions == null) {
            synchronized ($writeLock) {
                if (rootDefinitions == null) {
                    rootDefinitions = JsonSchemaFilter.builder().definitions(rules.getDefinitions()).build();
                }
            }
        }
        return rootDefinitions;
    }


    private String getTargetHostPath() {
        if (targetHostPath == null) {
            synchronized ($writeLock) {
                if (targetHostPath == null) {
                    targetHostPath = configService.getConfigPropertyAsOptional(ApiModeConfigProperty.TARGET_HOST)
                        .map(s -> {
                            try {
                                if (!s.startsWith("https://")) {
                                    s = "https://" + s;
                                }
                                return new URL(s).getPath();
                            } catch (MalformedURLException e) {
                                log.log(Level.WARNING, "Invalid API_HOST: " + s, e);
                                //shouldn't happen
                                return "";
                            }
                        })
                        .orElse("");
                }
            }
        }
        return targetHostPath;
    }

    @VisibleForTesting
    String stripTargetHostPath(String path) {
        if (StringUtils.isBlank(getTargetHostPath())) {
            return path;
        } else {
            return path.replaceFirst("^" + getTargetHostPath(), "");
        }
    }

    @VisibleForTesting
    Predicate<Map.Entry<Endpoint, Pattern>> getHasPathTemplateMatchingUrl(URL url) {
        return (entry) -> {
            if (entry.getKey().getPathTemplate() != null) {
                Matcher matcher = entry.getValue().matcher(stripTargetHostPath(url.getPath()));
                if (matcher.matches()) {
                    // this should NOT match on empty path segments; eg "/foo//bar" should not match "/foo/{param}/bar"
                    boolean allParamsValid =
                        entry.getKey().getPathParameterSchemasOptional()
                            .map(schemas -> schemas.entrySet().stream()
                                .allMatch(paramSchema -> parameterSchemaUtils.validate(paramSchema.getValue(), matcher.group(paramSchema.getKey()))))
                            .orElse(true);

                    //q: need to catch possible IllegalArgumentException if path parameter defined in `pathParameterSchemas`
                    // not in the path template??

                    return allParamsValid &&
                        allowedQueryParams(entry.getKey(), URLUtils.parseQueryParams(url));
                }
            }
            return false;
        };
    }

    @VisibleForTesting
    Predicate<Endpoint> allowsHttpMethod(@NonNull String httpMethod) {
        return (endpoint) ->
            endpoint.getAllowedMethods()
                .map(methods -> methods.stream().map(String::toUpperCase).collect(Collectors.toList())
                    .contains(httpMethod.toUpperCase()))
                .orElse(true);
    }

    @VisibleForTesting
    Predicate<Map.Entry<Endpoint, Pattern>> getHasPathRegexMatchingUrl(String relativeUrl) {
        return (entry) ->
            entry.getKey().getPathRegex() != null && entry.getValue().matcher(relativeUrl).matches();
    }


    @VisibleForTesting
    Optional<Pair<Pattern, Endpoint>> getEndpoint(String httpMethod, URL url) {
        String relativeUrl = stripTargetHostPath(URLUtils.relativeURL(url));

        Predicate<Map.Entry<Endpoint, Pattern>> hasPathRegexMatchingUrl =
            getHasPathRegexMatchingUrl(relativeUrl);

        Predicate<Map.Entry<Endpoint, Pattern>> hasPathTemplateMatchingUrl =
            getHasPathTemplateMatchingUrl(url);

        Predicate<Endpoint> allowsHttpMethod = allowsHttpMethod(httpMethod);

        return getCompiledAllowedEndpoints().entrySet().stream()
            .filter(entry -> hasPathRegexMatchingUrl.test(entry) || hasPathTemplateMatchingUrl.test(entry))
            .filter(entry -> allowedQueryParams(entry.getKey(), URLUtils.parseQueryParams(url))) // redundant in the pathTemplate case
            .filter(entry -> allowsHttpMethod.test(entry.getKey()))
            .findAny()
            .map(entry -> Pair.of(entry.getValue(), entry.getKey()));
    }

}
