package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Rules1;
import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.Transform;
import co.worklytics.psoxy.utils.URLUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

@Log
@RequiredArgsConstructor //for tests to compile for now
public class SanitizerImpl implements Sanitizer {

    @Getter
    final Options options;

    //NOTE: JsonPath seems to be threadsafe
    //  - https://github.com/json-path/JsonPath/issues/384
    //  - https://github.com/json-path/JsonPath/issues/187 (earlier issue fixing stuff that wasn't thread-safe)

    List<Pair<Pattern, List<JsonPath>>> compiledPseudonymizations;
    List<Pair<Pattern, List<JsonPath>>> compiledRedactions;
    List<Pair<Pattern, List<JsonPath>>> compiledEmailHeaderPseudonymizations;
    List<Pair<Pattern, List<JsonPath>>> compiledPseudonymizationsWithOriginals;
    List<Pattern> compiledAllowedEndpoints;

    private final Object $writeLock = new Object[0];
    List<Pair<Pattern, Rules2.Endpoint>> compiledEndpointRules;
    Map<Transform, List<JsonPath>> compiledTransforms = new ConcurrentHashMap<>();

    @AssistedInject
    public SanitizerImpl(HashUtils hashUtils, @Assisted Options options) {
        this.hashUtils = hashUtils;
        this.options = options;
    }

    @Getter(onMethod_ = {@VisibleForTesting})
    @Inject Configuration jsonConfiguration;


    @Inject
    HashUtils hashUtils;

    List<JsonPath> applicablePaths(@NonNull List<Pair<Pattern, List<JsonPath>>> rules,
                                   @NonNull String relativeUrl) {
        return rules.stream()
            .filter(compiled -> compiled.getKey().asMatchPredicate().test(relativeUrl))
            .map(Pair::getValue)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    List<Pattern> getCompiledAllowedEndpoints() {
        if (compiledAllowedEndpoints == null) {
            synchronized ($writeLock) {
                if (options.getRules() instanceof Rules2) {
                    compiledAllowedEndpoints = ((Rules2) options.getRules()).getEndpoints().stream()
                        .map(Rules2.Endpoint::getPathRegex)
                        .map(regex -> Pattern.compile(regex, CASE_INSENSITIVE))
                        .collect(Collectors.toList());
                } else {
                    compiledAllowedEndpoints = ((Rules1) options.getRules()).getAllowedEndpointRegexes().stream()
                        .map(regex -> Pattern.compile(regex, CASE_INSENSITIVE))
                        .collect(Collectors.toList());
                }
            }
        }
        return compiledAllowedEndpoints;
    }



    @Override
    public boolean isAllowed(@NonNull URL url) {
        String relativeUrl = URLUtils.relativeURL(url);
        return isAllowAll(options.getRules())
            || getCompiledAllowedEndpoints().stream().anyMatch(p -> p.matcher(relativeUrl).matches());
    }

    boolean isAllowAll(RuleSet rules) {
        if (rules instanceof Rules1) {
            Rules1 rules1 = (Rules1) rules;
            return (rules1.getAllowedEndpointRegexes() == null|| rules1.getAllowedEndpointRegexes().isEmpty());
        } else {
            return ((Rules2) rules).getAllowAllEndpoints();
        }
    }


    @Override
    public String sanitize(@NonNull URL url, @NonNull String jsonResponse) {
        //extra check ...
        if (!isAllowed(url)) {
            throw new IllegalStateException(String.format("Sanitizer called to sanitize response that should not have been retrieved: %s", url.toString()));
        }
        if (StringUtils.isEmpty(jsonResponse)) {
            // Nothing to do
            return jsonResponse;
        }

        if (getOptions().getRules() instanceof Rules1) {
            return legacyTransform(url, jsonResponse);
        } else {
            return transform(url, jsonResponse);
        }
    }

    synchronized List<Pair<Pattern, Rules2.Endpoint>> getEndpointRules() {
        if (compiledEndpointRules == null) {
            synchronized ($writeLock) {
                if (compiledEndpointRules == null) {
                    compiledEndpointRules = ((Rules2) options.getRules()).getEndpoints().stream()
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
                path.delete(document, jsonConfiguration);
            }
        } else {
            MapFunction f;
            if (transform instanceof Transform.Pseudonymize) {
                //curry the defaultScopeId from the transform into the pseudonymization method
                f =
                    ((Transform.Pseudonymize) transform).getIncludeOriginal() ? this::pseudonymizeWithOriginalToJson : this::pseudonymizeToJson;
            } else if (transform instanceof Transform.PseudonymizeEmailHeader) {
                f = this::pseudonymizeEmailHeaderToJson;
            } else if (transform instanceof Transform.RedactRegexMatches) {
                f = getRedactRegexMatches((Transform.RedactRegexMatches) transform);
            } else if (transform instanceof Transform.FilterTokenByRegex) {
                f = getFilterTokenByRegex((Transform.FilterTokenByRegex) transform);
            } else {
                throw new IllegalArgumentException("Unknown transform type: " + transform.getClass().getName());
            }
            for (JsonPath path : paths) {
                path.map(document, f, jsonConfiguration);
            }
        }
        return document;
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




    String legacyTransform(@NonNull URL url, @NonNull String jsonResponse) {        //q: move this stuff to initialization / DI provider??
        String relativeUrl = URLUtils.relativeURL(url);

        List<JsonPath> pseudonymizationsToApply =
            applicablePaths(getCompiledPseudonymizations(), relativeUrl);

        List<JsonPath> redactionsToApply =
            applicablePaths(getCompiledRedactions(), relativeUrl);

        List<JsonPath> emailHeaderPseudonymizationsToApply =
            applicablePaths(getCompiledEmailHeaderPseudonymizations(), relativeUrl);

        List<JsonPath> pseudonymizationWithOriginalsToApply =
            applicablePaths(getCompiledPseudonymizationsWithOriginals(), relativeUrl);

        if (pseudonymizationsToApply.isEmpty()
            && redactionsToApply.isEmpty()
            && emailHeaderPseudonymizationsToApply.isEmpty()
            && pseudonymizationWithOriginalsToApply.isEmpty()) {
            return jsonResponse;
        } else {
            Object document = jsonConfiguration.jsonProvider().parse(jsonResponse);

            for (JsonPath redaction : redactionsToApply) {
                redaction
                    .delete(document, jsonConfiguration);
            }

            //TODO: error handling within the map functions. any exceptions thrown within the map
            //      function seem to be suppressed, and an empty [] left as the 'document'.
            // ideas:
            // jsonConfiguration.addEvaluationListeners(); -->

            for (JsonPath pseudonymization : pseudonymizationsToApply) {
               pseudonymization
                    .map(document, this::pseudonymizeToJson, jsonConfiguration);
            }

            for (JsonPath pseudonymization : emailHeaderPseudonymizationsToApply) {
                pseudonymization
                    .map(document, this::pseudonymizeEmailHeaderToJson, jsonConfiguration);
            }

            for (JsonPath pseudonymization : pseudonymizationWithOriginalsToApply) {
                pseudonymization
                    .map(document, this::pseudonymizeWithOriginalToJson, jsonConfiguration);
            }

            return jsonConfiguration.jsonProvider().toJson(document);
        }
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
        return pseudonymize(value, false);
    }

    public PseudonymizedIdentity pseudonymize(Object value, boolean includeOriginal) {
        if (value == null) {
            return null;
        }

        Preconditions.checkArgument(value instanceof String || value instanceof Number,
            "Value must be some basic type (eg JSON leaf, not node)");

        PseudonymizedIdentity.PseudonymizedIdentityBuilder builder = PseudonymizedIdentity.builder();

        String canonicalValue, scope;
        //q: this auto-detect a good idea? Or invert control and let caller specify with a header
        // or something??
        //NOTE: use of EmailAddressValidator/Parser here is probably overly permissive, as there
        // are many cases where we expect simple emails (eg, alice@worklytics.co), not all the
        // possible variants with personal names / etc that may be allowed in email header values
        if (value instanceof String && EmailAddressValidator.isValid((String) value)) {

            String domain = EmailAddressParser.getDomain((String) value, EmailAddressCriteria.DEFAULT, true);
            builder.domain(domain);
            scope = PseudonymizedIdentity.EMAIL_SCOPE;

            //NOTE: lower-case here is NOT stipulated by RFC
            canonicalValue =
                EmailAddressParser.getLocalPart((String) value, EmailAddressCriteria.DEFAULT, true)
                    .toLowerCase()
                + "@"
                + domain.toLowerCase();

            //q: do something with the personal name??
            // NO --> it is not going to be reliable (except for From, will fill with whatever
            // sender has for the person in their Contacts), and in enterprise use-cases we
            // shouldn't need it for matching
        } else {
            canonicalValue = value.toString();
            scope = options.getDefaultScopeId();
        }

        if (canonicalValue != null) {
            builder.scope(scope);
            builder.hash(hashUtils.hash(canonicalValue, options.getPseudonymizationSalt(), asLegacyScope(scope)));
        }

        if (includeOriginal) {
            builder.original(Objects.toString(value));
        }

        return builder.build();
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
        return configuration.jsonProvider().toJson(pseudonymize(value, true));
    }


    String pseudonymizeEmailHeaderToJson(@NonNull Object value, @NonNull Configuration configuration) {
        return configuration.jsonProvider().toJson(pseudonymizeEmailHeader(value));
    }

    private List<Pair<Pattern, List<JsonPath>>> compile(List<Rules1.Rule> rules) {
        return rules.stream()
            .map(configured -> Pair.of(Pattern.compile(configured.getRelativeUrlRegex(), CASE_INSENSITIVE),
                configured.getJsonPaths().stream()
                    .map(JsonPath::compile)
                    .collect(Collectors.toList())))
            .collect(Collectors.toList());
    }

    @Override
    public PseudonymizedIdentity pseudonymize(@NonNull String value) {
        return pseudonymize((Object)  value);
    }

    @Override
    public PseudonymizedIdentity pseudonymize(@NonNull Number value) {
        return pseudonymize((Object) value);
    }

    List<Pair<Pattern, List<JsonPath>>>  getCompiledPseudonymizations() {
        if (compiledPseudonymizations == null) {
            synchronized ($writeLock){
                if (compiledPseudonymizations == null) {
                    compiledPseudonymizations = compile(((Rules1) options.getRules()).getPseudonymizations());
                }
            }
        }
        return compiledPseudonymizations;
    }
    List<Pair<Pattern, List<JsonPath>>>  getCompiledPseudonymizationsWithOriginals() {
        if (compiledPseudonymizationsWithOriginals == null) {
            synchronized ($writeLock){
                if (compiledPseudonymizationsWithOriginals == null) {
                    compiledPseudonymizationsWithOriginals =
                        compile(((Rules1) options.getRules()).getPseudonymizationWithOriginals());
                }
            }
        }
        return compiledPseudonymizationsWithOriginals;
    }
    List<Pair<Pattern, List<JsonPath>>>  getCompiledRedactions() {
        if (compiledRedactions == null) {
            synchronized ($writeLock){
                if (compiledRedactions == null) {
                    compiledRedactions = compile(((Rules1) options.getRules()).getRedactions());
                }
            }
        }
        return compiledRedactions;
    }

    List<Pair<Pattern, List<JsonPath>>>  getCompiledEmailHeaderPseudonymizations() {
        if (compiledEmailHeaderPseudonymizations == null) {
            synchronized ($writeLock){
                if (compiledEmailHeaderPseudonymizations == null) {
                    compiledEmailHeaderPseudonymizations = compile(((Rules1) options.getRules()).getEmailHeaderPseudonymizations());
                }
            }
        }
        return compiledEmailHeaderPseudonymizations;
    }
}
