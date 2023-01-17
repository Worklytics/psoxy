package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.Transform;
import co.worklytics.psoxy.utils.URLUtils;
import com.avaulta.gateway.pseudonyms.*;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.SchemaRuleUtils;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.MapFunction;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressCriteria;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressParser;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressValidator;

import javax.inject.Inject;
import javax.mail.internet.InternetAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

@Log
@RequiredArgsConstructor //for tests to compile for now
public class SanitizerImpl implements Sanitizer {

    @Getter
    final ConfigurationOptions configurationOptions;

    //NOTE: JsonPath seems to be threadsafe
    //  - https://github.com/json-path/JsonPath/issues/384
    //  - https://github.com/json-path/JsonPath/issues/187 (earlier issue fixing stuff that wasn't thread-safe)

    Map<Rules2.Endpoint, Pattern> compiledAllowedEndpoints;

    private final Object $writeLock = new Object[0];
    List<Pair<Pattern, Rules2.Endpoint>> compiledEndpointRules;
    Map<Transform, List<JsonPath>> compiledTransforms = new ConcurrentHashMap<>();

    @AssistedInject
    public SanitizerImpl(HashUtils hashUtils, @Assisted ConfigurationOptions configurationOptions) {
        this.hashUtils = hashUtils;
        this.configurationOptions = configurationOptions;
    }

    @Getter(onMethod_ = {@VisibleForTesting})
    @Inject Configuration jsonConfiguration;

    @Inject
    HashUtils hashUtils;

    @Inject
    ReversibleTokenizationStrategy reversibleTokenizationStrategy;
    @Inject
    DeterministicTokenizationStrategy deterministicTokenizationStrategy;
    @Inject
    UrlSafeTokenPseudonymEncoder urlSafePseudonymEncoder;

    @Inject
    SchemaRuleUtils schemaRuleUtils;

    Map<Rules2.Endpoint, Pattern> getCompiledAllowedEndpoints() {
        if (compiledAllowedEndpoints == null) {
            synchronized ($writeLock) {
                if (configurationOptions.getRules() instanceof Rules2) {
                    compiledAllowedEndpoints = ((Rules2) configurationOptions.getRules()).getEndpoints().stream()
                        .collect(Collectors.toMap(Function.identity(),
                            endpoint -> Pattern.compile(endpoint.getPathRegex(), CASE_INSENSITIVE)));
                } else {
                    throw new IllegalStateException("Rules must be of type Rules2");
                }
            }
        }
        return compiledAllowedEndpoints;
    }



    @Override
    public boolean isAllowed(@NonNull String httpMethod, @NonNull URL url) {
        String relativeUrl = URLUtils.relativeURL(url);

        Rules2 rules = ((Rules2) configurationOptions.getRules());

        if (rules.getAllowAllEndpoints()) {
            return true;
        } else {
            return getCompiledAllowedEndpoints().entrySet().stream()
                .filter(entry -> entry.getValue().matcher(relativeUrl).matches())
                .filter(entry -> entry.getKey().getAllowedMethods()
                    .map(methods -> methods.stream().map(String::toUpperCase).collect(Collectors.toList())
                            .contains(httpMethod.toUpperCase()))
                    .orElse(true))
                .filter(entry -> entry.getKey().getAllowedQueryParamsOptional()
                    .map(allowedParams -> allowedParams.containsAll(URLUtils.queryParamNames(url))).orElse(true))
                .findAny().isPresent();
        }
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

    synchronized List<Pair<Pattern, Rules2.Endpoint>> getEndpointRules() {
        if (compiledEndpointRules == null) {
            synchronized ($writeLock) {
                if (compiledEndpointRules == null) {
                    compiledEndpointRules = ((Rules2) configurationOptions.getRules()).getEndpoints().stream()
                        .map(endpoint -> Pair.of(Pattern.compile(endpoint.getPathRegex(), CASE_INSENSITIVE), endpoint))
                        .collect(Collectors.toList());
                }
            }
        }
        return compiledEndpointRules;
    }


    String transform(@NonNull URL url, @NonNull String jsonResponse) {
        String relativeUrl = URLUtils.relativeURL(url);
        Optional<Pair<Pattern, Rules2.Endpoint>> matchingEndpoint = getEndpointRules().stream()
            .filter(compiledEndpoint -> compiledEndpoint.getKey().asMatchPredicate().test(relativeUrl))
            .findFirst();

        return matchingEndpoint.map(match -> {

            Object document = jsonConfiguration.jsonProvider().parse(jsonResponse);

            for (Transform transform : match.getValue().getTransforms()) {
                applyTransform(transform, document);
            }

            return jsonConfiguration.jsonProvider().toJson(document);
        }).orElse(jsonResponse);
    }


    Object applyTransform(Transform transform, Object document ) {
        List<JsonPath> paths = compiledTransforms.computeIfAbsent(transform,
            t -> t.getJsonPaths().stream()
                .map(JsonPath::compile)
                .collect(Collectors.toList()));

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
        } else if (transform instanceof Transform.FilterTokenByRegex) {
            f = getFilterTokenByRegex((Transform.FilterTokenByRegex) transform);
        } else if (transform instanceof Transform.FilterBySchema) {
            f = getFilterBySchema((Transform.FilterBySchema) transform);
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

    MapFunction getFilterBySchema(Transform.FilterBySchema transform) {
        return (s, jsonConfiguration) -> schemaRuleUtils.filterBySchema(s, transform.getSchema());
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
            if (EmailAddressParser.isValidAddressList((String) value, EmailAddressCriteria.DEFAULT)) {
                InternetAddress[] addresses =
                    EmailAddressParser.extractHeaderAddresses((String) value, EmailAddressCriteria.DEFAULT, true);
                return Arrays.stream(addresses)
                    .map(InternetAddress::getAddress)
                    .map(this::pseudonymize)
                    .collect(Collectors.toList());
            } else {
                log.log(Level.WARNING, "Valued matched by emailHeader rule is not valid address list, but not blank");
                return null;
            }
        }
    }

    public PseudonymizedIdentity pseudonymize(Object value) {
        return pseudonymize(value, Transform.Pseudonymize.builder().build());
    }

    public MapFunction getPseudonymize(Transform.Pseudonymize transformOptions) {
        return (Object s, Configuration configuration) -> {
            PseudonymizedIdentity pseudonymizedIdentity = pseudonymize(s, transformOptions);
            if (transformOptions.getEncoding() == PseudonymEncoder.Implementations.JSON) {
                return configuration.jsonProvider().toJson(pseudonymizedIdentity);
            } else if (transformOptions.getEncoding() == PseudonymEncoder.Implementations.URL_SAFE_TOKEN) {
                //TODO: exploits that this was already encoded with UrlSafeTokenPseudonymEncoder
                return pseudonymizedIdentity.getReversible();
            } else {
                throw new RuntimeException("Unsupported pseudonym implementation: " + getConfigurationOptions().getPseudonymImplementation());
            }

        };
    }

    String emailCanonicalization(String original) {
        String domain = EmailAddressParser.getDomain(original, EmailAddressCriteria.DEFAULT, true);

        //NOTE: lower-case here is NOT stipulated by RFC
        return  EmailAddressParser.getLocalPart(original, EmailAddressCriteria.DEFAULT, true)
            .toLowerCase()
            + "@"
            + domain.toLowerCase();

    }

    public PseudonymizedIdentity pseudonymize(Object value, Transform.Pseudonymize transformOptions) {
        if (value == null) {
            return null;
        }

        Preconditions.checkArgument(value instanceof String || value instanceof Number,
            "Value must be some basic type (eg JSON leaf, not node)");

        PseudonymizedIdentity.PseudonymizedIdentityBuilder builder = PseudonymizedIdentity.builder();

        String scope;
        //q: this auto-detect a good idea? Or invert control and let caller specify with a header
        // or something??
        //NOTE: use of EmailAddressValidator/Parser here is probably overly permissive, as there
        // are many cases where we expect simple emails (eg, alice@worklytics.co), not all the
        // possible variants with personal names / etc that may be allowed in email header values

        Function<String, String> canonicalization;
        String domain = null;
        if (duckTypesAsEmails(value)) {
            canonicalization = this::emailCanonicalization;
            domain = EmailAddressParser.getDomain((String) value, EmailAddressCriteria.DEFAULT, true);
            builder.domain(domain);
            scope = PseudonymizedIdentity.EMAIL_SCOPE;
            //q: do something with the personal name??
            // NO --> it is not going to be reliable (except for From, will fill with whatever
            // sender has for the person in their Contacts), and in enterprise use-cases we
            // shouldn't need it for matching
        } else {
            canonicalization = Function.identity();
            scope = configurationOptions.getDefaultScopeId();
        }

        builder.scope(scope);
        if (getConfigurationOptions().getPseudonymImplementation() == PseudonymImplementation.LEGACY) {
           builder.hash(hashUtils.hash(canonicalization.apply(value.toString()),
               configurationOptions.getPseudonymizationSalt(), asLegacyScope(scope)));
        } else if (getConfigurationOptions().getPseudonymImplementation() == PseudonymImplementation.DEFAULT) {

           builder.hash(urlSafePseudonymEncoder.encode(
               Pseudonym.builder()
                   .hash(deterministicTokenizationStrategy.getToken(value.toString(), canonicalization))
                   .build()));

        } else {
            throw new RuntimeException("Unsupported pseudonym implementation: " + getConfigurationOptions().getPseudonymImplementation());
        }

        if (transformOptions.getIncludeReversible()) {
            builder.reversible(urlSafePseudonymEncoder.encode(
                Pseudonym.builder()
                    .reversible(reversibleTokenizationStrategy.getReversibleToken(value.toString(), canonicalization))
                    .domain(domain)
                    .build()));
        }

        if (transformOptions.getIncludeOriginal()) {
            builder.original(Objects.toString(value));
        }

        return builder.build();
    }

    boolean duckTypesAsEmails(Object value) {
        return value instanceof String && EmailAddressValidator.isValid((String) value);
    }

    //converts 'scope' to legacy value (eg, equivalents to original Worklytics scheme, where no scope
    // meant 'email'
    private String asLegacyScope(@NonNull String scope) {
        return scope.equals(PseudonymizedIdentity.EMAIL_SCOPE) ? "" : scope;
    }

    @VisibleForTesting
    public String pseudonymizeToJson(Object value, @NonNull Configuration configuration) {
        return configuration.jsonProvider().toJson(pseudonymize(value));
    }

    public String pseudonymizeWithOriginalToJson(Object value, @NonNull Configuration configuration) {
        return configuration.jsonProvider().toJson(pseudonymize(value, Transform.Pseudonymize.builder().includeOriginal(true).build()));
    }


    String pseudonymizeEmailHeaderToJson(@NonNull Object value, @NonNull Configuration configuration) {
        return configuration.jsonProvider().toJson(pseudonymizeEmailHeader(value));
    }
    @Override
    public PseudonymizedIdentity pseudonymize(@NonNull String value) {
        return pseudonymize((Object)  value);
    }

    @Override
    public PseudonymizedIdentity pseudonymize(@NonNull Number value) {
        return pseudonymize((Object) value);
    }


}
