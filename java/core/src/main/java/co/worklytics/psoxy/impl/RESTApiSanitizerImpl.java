package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.rules.RESTRules;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilterUtils;
import com.avaulta.gateway.rules.transforms.Transform;
import co.worklytics.psoxy.utils.URLUtils;
import com.avaulta.gateway.pseudonyms.*;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.jayway.jsonpath.Configuration;
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
import org.hazlewood.connor.bottema.emailaddress.EmailAddressCriteria;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressParser;

import javax.inject.Inject;
import javax.mail.internet.InternetAddress;
import java.net.URL;
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


    //NOTE: JsonPath seems to be threadsafe
    //  - https://github.com/json-path/JsonPath/issues/384
    //  - https://github.com/json-path/JsonPath/issues/187 (earlier issue fixing stuff that wasn't thread-safe)

    Map<Endpoint, Pattern> compiledAllowedEndpoints;

    private final Object $writeLock = new Object[0];
    Map<Transform, List<JsonPath>> compiledTransforms = new ConcurrentHashMap<>();

    JsonSchemaFilterUtils.JsonSchemaFilter rootDefinitions;


    @AssistedInject
    public RESTApiSanitizerImpl(@Assisted RESTRules rules,
                                @Assisted Pseudonymizer pseudonymizer) {
        this.rules = rules;
        this.pseudonymizer = pseudonymizer;
    }

    @Getter(onMethod_ = {@VisibleForTesting})
    @Inject Configuration jsonConfiguration;

    @Inject
    ReversibleTokenizationStrategy reversibleTokenizationStrategy;
    @Inject
    UrlSafeTokenPseudonymEncoder urlSafePseudonymEncoder;
    @Inject
    JsonSchemaFilterUtils jsonSchemaFilterUtils;




    @Override
    public boolean isAllowed(@NonNull String httpMethod, @NonNull URL url) {
        String relativeUrl = URLUtils.relativeURL(url);

        Predicate<Map.Entry<Endpoint, Pattern>> hasPathRegexMatchingUrl =
            getHasPathRegexMatchingUrl(relativeUrl);

        Predicate<Map.Entry<Endpoint, Pattern>> hasPathTemplateMatchingUrl =
            getHasPathTemplateMatchingUrl(url);

        Predicate<Endpoint> allowsHttpMethod = allowsHttpMethod(httpMethod);


        if (rules.getAllowAllEndpoints()) {
            return true;
        } else {
            return getCompiledAllowedEndpoints().entrySet().stream()
                .filter(entry -> hasPathRegexMatchingUrl.test(entry) || hasPathTemplateMatchingUrl.test(entry))
                .filter(entry -> allowedQueryParams(entry.getKey(), URLUtils.queryParamNames(url))) // redundant in the pathTemplate case
                .filter(entry -> allowsHttpMethod.test(entry.getKey()))
                .findAny().isPresent();
        }
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
    Predicate<Map.Entry<Endpoint, Pattern>> getHasPathTemplateMatchingUrl(URL url) {
        return (entry) ->
            entry.getKey().getPathTemplate() != null && entry.getValue().matcher(url.getPath()).matches()
                && allowedQueryParams(entry.getKey(), URLUtils.queryParamNames(url));
    }


    @Override
    public String sanitize(String httpMethod, URL url, String jsonResponse) {        //extra check ...
        if (!isAllowed(httpMethod, url)) {
            throw new IllegalStateException(String.format("Sanitizer called to sanitize response that should not have been retrieved: %s", url.toString()));
        }
        if (StringUtils.isEmpty(jsonResponse)) {
            // Nothing to do
            return jsonResponse;
        }

        return transform(url, jsonResponse);

    }

    String transform(@NonNull URL url, @NonNull String jsonResponse) {
        String relativeUrl = URLUtils.relativeURL(url);
        Optional<Pair<Pattern, Endpoint>> matchingEndpoint =
            getCompiledAllowedEndpoints().entrySet().stream()
            .filter(entry -> entry.getKey().getPathRegex() != null && entry.getValue().matcher(relativeUrl).matches()
                || (entry.getKey().getPathTemplate() != null && entry.getValue().matcher(url.getPath()).matches() && allowedQueryParams(entry.getKey(), URLUtils.queryParamNames(url))))
            .findFirst()
                .map(entry -> Pair.of(entry.getValue(), entry.getKey()));

        return matchingEndpoint.map(match -> {
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


    Object applyTransform(Transform transform, Object document ) {
        List<JsonPath> paths = compiledTransforms.computeIfAbsent(transform,
            t -> t.getJsonPaths().stream()
                .map(JsonPath::compile)
                .collect(Collectors.toList()));

        if (transform.getApplyOnlyWhen() != null) {
            Object filterResult = JsonPath.compile(transform.getApplyOnlyWhen()).read(document);

            ArrayList<?> results = (ArrayList<?>) filterResult;
            if (results != null && results.size() == 0) {
                return document;
            }
        }

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
        return document;
    }

    @VisibleForTesting
    public MapFunction getTransformImpl(Transform transform) {
        MapFunction f;
        if (transform instanceof Transform.Pseudonymize) {
            //curry the defaultScopeId from the transform into the pseudonymization method
            f = getPseudonymize((Transform.Pseudonymize) transform);
        } else if (transform instanceof Transform.PseudonymizeEmailHeader) {
            f = this::pseudonymizeEmailHeaderToJson;
        } else if (transform instanceof Transform.RedactRegexMatches) {
            f = getRedactRegexMatches((Transform.RedactRegexMatches) transform);
        } else if (transform instanceof Transform.RedactExceptSubstringsMatchingRegexes) {
            f = getRedactExceptSubstringsMatchingRegexes((Transform.RedactExceptSubstringsMatchingRegexes) transform);
        } else if (transform instanceof Transform.FilterTokenByRegex) {
            f = getFilterTokenByRegex((Transform.FilterTokenByRegex) transform);
        } else if (transform instanceof Transform.Tokenize) {
            f = getTokenize((Transform.Tokenize) transform);
        } else {
            throw new IllegalArgumentException("Unknown transform type: " + transform.getClass().getName());
        }
        return f;
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
            .map(p -> ".*(" + p + ").*") //wrap in .* to match anywhere in the string
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
                    .map( p -> p.matcher((String) s))
                    .filter(Matcher::matches)
                    .findFirst()
                    .map(m -> m.group(1))
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

    public MapFunction getPseudonymize(Transform.Pseudonymize transformOptions) {
        return (Object s, Configuration configuration) -> {
            PseudonymizedIdentity pseudonymizedIdentity = pseudonymizer.pseudonymize(s, transformOptions);
            if (transformOptions.getEncoding() == PseudonymEncoder.Implementations.JSON) {
                return configuration.jsonProvider().toJson(pseudonymizedIdentity);
            } else if (transformOptions.getEncoding() == PseudonymEncoder.Implementations.URL_SAFE_TOKEN) {
                //TODO: exploits that this was already encoded with UrlSafeTokenPseudonymEncoder
                return ObjectUtils.firstNonNull(pseudonymizedIdentity.getReversible(), pseudonymizedIdentity.getHash());
            } else {
                throw new RuntimeException("Unsupported pseudonym implementation: " + transformOptions.getEncoding());
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
            if (EmailAddressParser.isValidAddressList((String) value, EmailAddressCriteria.RECOMMENDED)) {
                InternetAddress[] addresses =
                    EmailAddressParser.extractHeaderAddresses((String) value, EmailAddressCriteria.RECOMMENDED, true);
                return Arrays.stream(addresses)
                    .map(InternetAddress::getAddress)
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

    //TODO: improve this; some special chars outside of {} are not accounted for
    final String SPECIAL_CHAR_CLASS = "[\\.\\^\\$\\<\\>\\*\\+\\[\\]\\(\\)\\+\\-\\=\\?\\!]";

    @VisibleForTesting
    String effectiveRegex(Endpoint endpoint) {
        return Optional.ofNullable(endpoint.getPathRegex())
            .orElseGet(() -> "^" +
                endpoint.getPathTemplate()
                    .replaceAll(SPECIAL_CHAR_CLASS, "\\\\$0")
                    .replaceAll("\\{.*?\\}", "[^/]+") + "$");
    }

    boolean allowedQueryParams(Endpoint endpoint, List<String> queryParams) {
        return endpoint.getAllowedQueryParamsOptional()
            .map(allowedParams -> allowedParams.containsAll(queryParams))
            .orElse(true);
    }

    JsonSchemaFilterUtils.JsonSchemaFilter getRootDefinitions() {
        if (rootDefinitions == null) {
            synchronized ($writeLock) {
                if (rootDefinitions == null) {
                    rootDefinitions = JsonSchemaFilterUtils.JsonSchemaFilter.builder().definitions(rules.getDefinitions()).build();
                }
            }
        }
        return rootDefinitions;
    }

}