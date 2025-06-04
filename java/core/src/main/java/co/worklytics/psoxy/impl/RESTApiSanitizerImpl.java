package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.PseudonymizedIdentity;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.RESTApiSanitizer;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.utils.URLUtils;
import co.worklytics.psoxy.utils.email.EmailAddress;
import co.worklytics.psoxy.utils.email.EmailAddressParser;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.JsonSchemaFilterUtils;
import com.avaulta.gateway.rules.ParameterSchemaUtils;
import com.avaulta.gateway.rules.transforms.EncryptIp;
import com.avaulta.gateway.rules.transforms.HashIp;
import com.avaulta.gateway.rules.PathTemplateUtils;
import com.avaulta.gateway.rules.transforms.Transform;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.MapFunction;
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
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    @Named("ipEncryptionStrategy")
    ReversibleTokenizationStrategy ipEncryptStrategy;
    @Inject
    @Named("ipHashStrategy")
    DeterministicTokenizationStrategy ipHashStrategy;


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
    public String sanitize(String httpMethod, URL url, String jsonResponse) {        //extra check ...
        if (!isAllowed(httpMethod, url)) {
            throw new IllegalStateException(String.format("Sanitizer called to sanitize response that should not have been retrieved: %s", url));
        }
        if (StringUtils.isEmpty(jsonResponse)) {
            // Nothing to do
            return jsonResponse;
        }

        return transform(httpMethod, url, jsonResponse);
    }

    String transform(@NonNull String httpMethod, @NonNull URL url, @NonNull String jsonResponse) {
        return getEndpoint(httpMethod, url).map(match -> {
            String filteredJson = match.getValue().getResponseSchemaOptional()
                .map(schema -> {
                    //q: this read
                    try {
                        return jsonSchemaFilterUtils.filterJsonBySchema(jsonResponse, schema, getRootDefinitions());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(jsonResponse);

            //q: more efficient to filter on `document` directly? problem is that our json path
            // library contract only specifies 'Object' for it's parsed document type; but in
            // practice, both it and our SchemaRuleUtils are using Jackson underneath - so expect
            // everything to work OK.

            //NOTE: `document` here is a `LinkedHashMap`

            if (ObjectUtils.isNotEmpty(match.getValue().getTransforms())) {
                Object document = jsonConfiguration.jsonProvider().parse(filteredJson);

                for (Transform transform : match.getValue().getTransforms()) {
                    applyTransform(transform, document);
                }

                filteredJson = jsonConfiguration.jsonProvider().toJson(document);
            }
            return filteredJson;

        }).orElse(jsonResponse);
    }


    Object applyTransform(Transform transform, Object document) {
        List<JsonPath> paths = compiledTransforms.computeIfAbsent(transform,
            t -> t.getJsonPaths().stream()
                .map(JsonPath::compile)
                .collect(Collectors.toList()));

        if (transformApplies(transform, document)) {
            if (transform instanceof Transform.Redact) {
                for (JsonPath path : paths) {
                    try {
                        path.delete(document, jsonConfiguration);
                    } catch (com.jayway.jsonpath.PathNotFoundException e) {
                        //expected if rule doesn't apply
                    }
                }
            } else {
                MapFunction f = getTransformImpl(transform);
                for (JsonPath path : paths) {
                    try {
                        path.map(document, f, jsonConfiguration);
                    } catch (com.jayway.jsonpath.PathNotFoundException e) {
                        //expected if rule doesn't apply
                    }
                }
            }
        }
        return document;
    }

    private static boolean transformApplies(Transform transform, Object document) {
        if (transform.getApplyOnlyWhen() != null) {
            Object filterResult = JsonPath.compile(transform.getApplyOnlyWhen()).read(document);

            ArrayList<?> results = (ArrayList<?>) filterResult;

            return results != null && !results.isEmpty();
        }

        return true;
    }

    @VisibleForTesting
    public MapFunction getTransformImpl(Transform transform) {
        MapFunction f;
        if (transform instanceof Transform.Pseudonymize) {
            //curry the defaultScopeId from the transform into the pseudonymization method
            f = getPseudonymize((Transform.Pseudonymize) transform);
        } else if (transform instanceof Transform.PseudonymizeEmailHeader) {
            f = this::pseudonymizeEmailHeaderToJson;
        } else if (transform instanceof Transform.PseudonymizeRegexMatches) {
            f = getPseudonymizeRegexMatches((Transform.PseudonymizeRegexMatches) transform);
        } else if (transform instanceof Transform.RedactRegexMatches) {
            f = getRedactRegexMatches((Transform.RedactRegexMatches) transform);
        } else if (transform instanceof Transform.RedactExceptPhrases) {
            f = getRedactExceptPhrases((Transform.RedactExceptPhrases) transform);
        } else if (transform instanceof Transform.RedactExceptSubstringsMatchingRegexes) {
            f = getRedactExceptSubstringsMatchingRegexes((Transform.RedactExceptSubstringsMatchingRegexes) transform);
        } else if (transform instanceof Transform.FilterTokenByRegex) {
            f = getFilterTokenByRegex((Transform.FilterTokenByRegex) transform);
        } else if (transform instanceof Transform.Tokenize) {
            f = getTokenize((Transform.Tokenize) transform);
        } else if (transform instanceof HashIp) {
            f = getHashIp((HashIp) transform);
        } else if (transform instanceof EncryptIp) {
            f = getEncryptIp((EncryptIp) transform);
        } else if (transform instanceof Transform.TextDigest) {
            f = getTextDigest((Transform.TextDigest) transform);
        }else {
            throw new IllegalArgumentException("Unknown transform type: " + transform.getClass().getName());
        }
        return f;
    }

    @VisibleForTesting
    MapFunction getEncryptIp(EncryptIp transform) {
        return (s, jsonConfiguration) -> {
            if (!(s instanceof String)) {
                if (s != null) {
                    log.warning("value matched by " + transform + " not of type String");
                }
                return null;
            } else if (StringUtils.isBlank((String) s)) {
                return s;
            } else {
                String canonicalizedIp = canonicalizeIp((String) s);
                if (canonicalizedIp == null) {
                    //not a valid IP
                    return null;
                }

                return urlSafePseudonymEncoder.encode(Pseudonym.builder()
                    .hash(ipHashStrategy.getToken(canonicalizedIp))
                    .reversible(ipEncryptStrategy.getReversibleToken(canonicalizedIp))
                    .build());
            }
        };
    }

    @VisibleForTesting
    MapFunction getHashIp(HashIp transform) {
        return (s, jsonConfiguration) -> {
            if (!(s instanceof String)) {
                if (s != null) {
                    log.warning("value matched by " + transform + " not of type String");
                }
                return null;
            } else if (StringUtils.isBlank((String) s)) {
                return s;
            } else {
                String canonicalizedIp = canonicalizeIp((String) s);
                if (canonicalizedIp == null) {
                    //not a valid IP
                    return null;
                }

                // Parse the IP address string and convert back to string to get the canonical form.
                return urlSafePseudonymEncoder.encode(Pseudonym.builder()
                    .hash(ipHashStrategy.getToken(canonicalizedIp))
                    .build());
            }
        };
    }

    String canonicalizeIp(String ip) {
        try {
            //TODO: force to textual IP address, and never permit hostnames?
            InetAddress address = InetAddress.getByName(ip);
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            //not a valid IP address
            log.warning("value matched by HashIP transform not a valid IP address: " + ip);
            return null;
        }
    }

    MapFunction getRedactExceptPhrases(Transform.RedactExceptPhrases transform) {

        //TODO: alternatively, all different patterns, and preserve ALL matches?
        // --> means we might enlarge, if the same phrase matches multiple times

        List<Pattern> patterns = transform.getAllowedPhrases().stream()
            .map(p -> "\\Q" + p + "\\E") // quote it
            .map(p -> "\\b(" + p + ")[\\s:]*\\b") //boundary match, with optional whitespace or colon at end
            .map(p -> ".*?" + p + ".*?") //wrap in .*? to match anywhere in the string, but reluctantly
            .map(p -> Pattern.compile(p, CASE_INSENSITIVE))
            .collect(Collectors.toList());

        return (s, jsonConfiguration) -> {
            if (!(s instanceof String)) {
                if (s != null) {
                    log.warning("value matched by " + transform + " not of type String");
                }
                return null;
            } else if (StringUtils.isBlank((String) s)) {
                return s;
            } else {
                return patterns.stream()
                    .map(p -> p.matcher((String) s))
                    .filter(Matcher::matches)
                    .map(m -> m.group(1)) //group 1, bc we created caputuring group in regex above
                    .collect(Collectors.joining(",")); //q: something better? if , in phrases, can't reparse

            }
        };
    }


    MapFunction getRedactRegexMatches(Transform.RedactRegexMatches transform) {
        List<Pattern> patterns = transform.getRedactions().stream().map(Pattern::compile).collect(Collectors.toList());
        return (s, jsonConfiguration) -> {
            if (!(s instanceof String)) {
                if (s != null) {
                    log.warning("value matched by " + transform + " not of type String");
                }
                return null;
            } else if (StringUtils.isBlank((String) s)) {
                return s;
            } else {
                String result = (String) s;
                for (Pattern p : patterns) {
                    result = p.matcher(result).replaceAll("");
                }
                return result;
            }
        };
    }

    MapFunction getRedactExceptSubstringsMatchingRegexes(Transform.RedactExceptSubstringsMatchingRegexes transform) {
        List<Pattern> patterns = transform.getExceptions().stream()
            .map(p -> ".*?(" + p + ").*?") //wrap in .*? to match anywhere in the string, but reluctantly
            .map(Pattern::compile).collect(Collectors.toList());
        return (s, jsonConfiguration) -> {
            if (!(s instanceof String)) {
                if (s != null) {
                    log.warning("value matched by " + transform + " not of type String");
                }
                return null;
            } else if (StringUtils.isBlank((String) s)) {
                return s;
            } else {
                return patterns.stream()
                    .map(p -> p.matcher((String) s))
                    .filter(Matcher::matches)
                    .map(m -> m.group(1)) //group 1, bc we created caputuring group in regex above
                    .sorted((a, b) -> Integer.compare(b.length(), a.length())) // longest first
                    .findFirst()
                    .orElse("");
            }
        };
    }


    MapFunction getFilterTokenByRegex(Transform.FilterTokenByRegex transform) {
        List<java.util.function.Predicate<String>> patterns =
            transform.getFilters().stream().map(Pattern::compile)
                .map(Pattern::asMatchPredicate)
                .collect(Collectors.toList());

        return (s, jsonConfiguration) -> {
            if (!(s instanceof String)) {
                if (s != null) {
                    log.warning("value matched by " + transform + " not of type String");
                }
                return null;
            } else if (StringUtils.isBlank((String) s)) {
                return s;
            } else {
                String result = (String) s;
                Stream<String> stream = Optional.ofNullable(transform.getDelimiter())
                    .map(delimiter -> Arrays.stream(result.split(delimiter)))
                    .orElse(Stream.of(result));

                return StringUtils.trimToNull(stream
                    .filter(token -> patterns.stream().anyMatch(p -> p.test(token)))
                    .collect(Collectors.joining(" ")));
            }
        };
    }

    MapFunction getTokenize(Transform.Tokenize transform) {
        Optional<Pattern> pattern = Optional.ofNullable(transform.getRegex()).map(Pattern::compile);
        return (s, jsonConfiguration) -> {
            if (!(s instanceof String)) {
                if (s != null) {
                    log.warning("value matched by " + transform + " not of type String");
                }
                return null;
            } else if (StringUtils.isBlank((String) s)) {
                return s;
            } else {
                String toTokenize = (String) s;
                Optional<Matcher> matcher = pattern
                    .map(p -> p.matcher(toTokenize));
                if (matcher.isPresent()) {
                    if (matcher.get().matches()) {
                        String token = urlSafePseudonymEncoder.encode(Pseudonym.builder()
                            .reversible(reversibleTokenizationStrategy.getReversibleToken(matcher.get().group(1)))
                            .build());
                        return toTokenize.replace(matcher.get().group(1), token);
                    } else {
                        return s;
                    }
                } else {
                    String token = urlSafePseudonymEncoder.encode(Pseudonym.builder()
                        .reversible(reversibleTokenizationStrategy.getReversibleToken(toTokenize))
                        .build());
                    return token;
                }
            }
        };
    }

    MapFunction getTextDigest(Transform.TextDigest transform) {
        return (s, jsonConfiguration) -> {
            if (!(s instanceof String toTokenize)) {
                if (s != null) {
                    log.warning("value matched by " + transform + " not of type String");
                }
                return null;
            } else {
                if (transform.getIsJsonEscaped() && transform.getJsonPathToProcessWhenEscaped() != null) {
                    DocumentContext jsonContext = JsonPath.parse(toTokenize);
                    List<String> texts = jsonContext.read(transform.getJsonPathToProcessWhenEscaped());

                    for (String text : texts) {
                        jsonContext.set(transform.getJsonPathToProcessWhenEscaped(), jsonConfiguration.jsonProvider().toJson(Transform.TextDigest.generate(text)));
                    }

                    return jsonContext.jsonString();
                } else {
                    return jsonConfiguration.jsonProvider().toJson(Transform.TextDigest.generate(toTokenize));
                }
            }
        };
    }

    public MapFunction getPseudonymize(Transform.Pseudonymize transformOptions) {
        return (Object s, Configuration configuration) -> {
            PseudonymizedIdentity pseudonymizedIdentity = pseudonymizer.pseudonymize(s, transformOptions);
            if (transformOptions.getEncoding() == PseudonymEncoder.Implementations.JSON) {
                return configuration.jsonProvider().toJson(pseudonymizedIdentity);
            } else if (transformOptions.getEncoding() == PseudonymEncoder.Implementations.URL_SAFE_TOKEN) {
                if (pseudonymizedIdentity.getReversible() != null
                    && getPseudonymizer().getOptions().getPseudonymImplementation() == PseudonymImplementation.LEGACY) {
                    // can't URL_SAFE_TOKEN encode reversible portion of pseudonym if LEGACY mode, bc
                    // URL_SAFE_TOKEN depends on 'hash' being encoded as prefix of the reversible;
                    // and reverisbles need the non-legacy
                    return configuration.jsonProvider().toJson(pseudonymizedIdentity);
                }
                //exploit that already reversibly encoded, including prefix
                return ObjectUtils.firstNonNull(pseudonymizedIdentity.getReversible(), urlSafePseudonymEncoder.encode(pseudonymizedIdentity.asPseudonym()));
            } else if (transformOptions.getEncoding() == PseudonymEncoder.Implementations.URL_SAFE_HASH_ONLY) {
                return pseudonymizedIdentity.getHash();
            } else {
                throw new RuntimeException("Unsupported pseudonym implementation: " + transformOptions.getEncoding());
            }

        };
    }


    public MapFunction getPseudonymizeRegexMatches(Transform.PseudonymizeRegexMatches transform) {
        Pattern pattern = Pattern.compile(transform.getRegex());

        return (Object s, Configuration configuration) -> {

            String fullString = (String) s;
            Matcher matcher = pattern.matcher(fullString);

            if (matcher.matches()) {
                String toPseudonymize;
                if (matcher.groupCount() > 0) {
                    toPseudonymize = matcher.group(1);
                } else {
                    toPseudonymize = matcher.group(0);
                }
                PseudonymizedIdentity pseudonymizedIdentity = pseudonymizer.pseudonymize(toPseudonymize, transform);

                String pseudonymizedString = urlSafePseudonymEncoder.encode(pseudonymizedIdentity.asPseudonym());

                if (matcher.groupCount() > 0) {
                    // return original, replacing match with encoded pseudonym
                    return fullString.replace(matcher.group(1), pseudonymizedString);
                } else {
                    return pseudonymizedString;
                }
            } else {
                //if no match, redact it
                return null;
            }
        };
    }

    @VisibleForTesting
    public String pseudonymizeToJson(Object value, @NonNull Configuration configuration) {
        return configuration.jsonProvider().toJson(pseudonymizer.pseudonymize(value));
    }

    public String pseudonymizeWithOriginalToJson(Object value, @NonNull Configuration configuration) {
        return configuration.jsonProvider().toJson(pseudonymizer.pseudonymize(value, Transform.Pseudonymize.builder().includeOriginal(true).build()));
    }


    String pseudonymizeEmailHeaderToJson(@NonNull Object value, @NonNull Configuration configuration) {
        return configuration.jsonProvider().toJson(pseudonymizeEmailHeader(value));
    }

    List<PseudonymizedIdentity> pseudonymizeEmailHeader(Object value) {
        if (value == null) {
            return null;
        }

        Preconditions.checkArgument(value instanceof String, "Value must be string");

        if (StringUtils.isBlank((String) value)) {
            return new ArrayList<>();
        } else {
            //NOTE: this does NOT seem to work for lists containing empty values (eg ",,"), which
            // per RFC should be allowed ....
            if (emailAddressParser.isValidAddressList((String) value)) {
                List<EmailAddress> addresses =
                    emailAddressParser.parseEmailAddressesFromHeader((String) value);
                return addresses.stream()
                    .map(EmailAddress::asFormattedString)
                    .map(pseudonymizer::pseudonymize)
                    .collect(Collectors.toList());
            } else {
                log.log(Level.WARNING, "Valued matched by emailHeader rule is not valid address list, but not blank");
                return null;
            }
        }
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
                    targetHostPath = configService.getConfigPropertyAsOptional(ProxyConfigProperty.TARGET_HOST)
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


    private Optional<Pair<Pattern, Endpoint>> getEndpoint(String httpMethod, URL url) {
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
