package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.PseudonymizedIdentity;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.utils.email.EmailAddress;
import co.worklytics.psoxy.utils.email.EmailAddressParser;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.transforms.EncryptIp;
import com.avaulta.gateway.rules.transforms.HashIp;
import com.avaulta.gateway.rules.transforms.Transform;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.MapFunction;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * utility methods that are common to multiple types of sanitizers
 *
 */
@Log
public class SanitizerUtils {

    @Getter(onMethod_ = {@VisibleForTesting})
    Configuration jsonConfiguration;
    ReversibleTokenizationStrategy reversibleTokenizationStrategy;
    UrlSafeTokenPseudonymEncoder urlSafePseudonymEncoder;
    EmailAddressParser emailAddressParser;
    ReversibleTokenizationStrategy ipEncryptStrategy;
    DeterministicTokenizationStrategy ipHashStrategy;

    @Inject
    public SanitizerUtils(
        Configuration jsonConfiguration,
        ReversibleTokenizationStrategy reversibleTokenizationStrategy,
        UrlSafeTokenPseudonymEncoder urlSafePseudonymEncoder,
        EmailAddressParser emailAddressParser,
        @Named("ipEncryptionStrategy") ReversibleTokenizationStrategy ipEncryptStrategy,
        @Named("ipHashStrategy") DeterministicTokenizationStrategy ipHashStrategy
    ) {
        this.jsonConfiguration = jsonConfiguration;
        this.reversibleTokenizationStrategy = reversibleTokenizationStrategy;
        this.urlSafePseudonymEncoder = urlSafePseudonymEncoder;
        this.emailAddressParser = emailAddressParser;
        this.ipEncryptStrategy = ipEncryptStrategy;
        this.ipHashStrategy = ipHashStrategy;
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
                if (transform.getIsJsonEscaped() && StringUtils.isNotBlank(transform.getJsonPathToProcessWhenEscaped())) {
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

    @VisibleForTesting
    public String pseudonymizeToJson(Object value, @NonNull Configuration configuration, @NonNull Pseudonymizer pseudonymizer) {
        return configuration.jsonProvider().toJson(pseudonymizer.pseudonymize(value));
    }

    public String pseudonymizeWithOriginalToJson(Object value, @NonNull Configuration configuration, @NonNull Pseudonymizer pseudonymizer) {
        return configuration.jsonProvider().toJson(pseudonymizer.pseudonymize(value, Transform.Pseudonymize.builder().includeOriginal(true).build()));
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


    // Matches valid IPv4 addresses
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^([0-9]{1,3}\\.){3}[0-9]{1,3}$"
    );

    // Matches unbracketed IPv6 addresses
    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^[0-9a-fA-F:.]+$"  // q: too permissive? this is what's rec'd by
    );


    String canonicalizeIp(String ip) {
        try {
            boolean maybeIpv4 = IPV4_PATTERN.matcher(ip).matches();
            boolean maybeIpv6 = IPV6_PATTERN.matcher(ip).matches();

            if (!maybeIpv4 && !maybeIpv6) {
                //not a valid IP address
                log.warning("value matched by HashIP transform not a valid IP address: " + ip);
                return null;
            }
            InetAddress address = InetAddress.getByName(ip);
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            //not a valid IP address
            log.warning("value matched by HashIP transform not a valid IP address: " + ip);
            return null;
        }
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

    @VisibleForTesting
    MapFunction getPseudonymizeEmailHeaderToJson(Pseudonymizer pseudonymizer, Transform.PseudonymizeEmailHeader transformOptions) {
        return (Object value, Configuration jsonConfiguration) -> {
            if (value == null) {
                return null;
            }

            com.google.api.client.util.Preconditions.checkArgument(value instanceof String, "Value must be string");

            if (StringUtils.isBlank((String) value)) {
                return ""; // empty string
            } else {
                // NOTE: this does NOT seem to work for lists containing empty values (eg ",,"),
                // which
                // per RFC should be allowed ....
                if (emailAddressParser.isValidAddressList((String) value)) {
                    List<EmailAddress> addresses =
                        emailAddressParser.parseEmailAddressesFromHeader((String) value);

                    //TODO: in v0.6, we should use a String instead of List<PseudonymizedIdentity>/List<String>;
                    // encode EVERYTHING according to encoding option, then join with comma back into a CSV string

                    return jsonConfiguration.jsonProvider()
                        .toJson(addresses.stream().map(EmailAddress::asFormattedString)
                            .map(pseudonymizer::pseudonymize).map(pseudonymizedIdentity -> {
                                if (transformOptions
                                    .getEncoding() == PseudonymEncoder.Implementations.URL_SAFE_TOKEN) {
                                    return urlSafePseudonymEncoder
                                        .encode(pseudonymizedIdentity.asPseudonym());
                                } else {
                                    return pseudonymizedIdentity;
                                }
                            }).collect(Collectors.toList()));
                } else {
                    log.log(Level.WARNING,
                        "Valued matched by emailHeader rule is not valid address list, but not blank");
                    return null;
                }
            }
        };
    }

    public MapFunction getPseudonymize(Pseudonymizer pseudonymizer, Transform.Pseudonymize transformOptions) {
        return (Object s, Configuration configuration) -> {
            PseudonymizedIdentity pseudonymizedIdentity = pseudonymizer.pseudonymize(s, transformOptions);
            if (transformOptions.getEncoding() == PseudonymEncoder.Implementations.JSON) {
                return configuration.jsonProvider().toJson(pseudonymizedIdentity);
            } else if (transformOptions.getEncoding() == PseudonymEncoder.Implementations.URL_SAFE_TOKEN) {
                if (pseudonymizedIdentity == null) {
                    // Forcing null instead of configuration.jsonProvider().toJson(null), which is going to return "null" string
                    return null;
                }
                if (pseudonymizedIdentity.getReversible() != null
                    && pseudonymizer.getOptions().getPseudonymImplementation() == PseudonymImplementation.LEGACY) {
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

    public MapFunction getPseudonymizeRegexMatches(Pseudonymizer pseudonymizer, Transform.PseudonymizeRegexMatches transform) {
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

    List<PseudonymizedIdentity> pseudonymizeEmailHeader(Pseudonymizer pseudonymizer, Object value) {
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

    @VisibleForTesting
    public MapFunction getTransformImpl(Pseudonymizer pseudonymizer, Transform transform) {
        MapFunction f;
        if (transform instanceof Transform.Pseudonymize) {
            //curry the defaultScopeId from the transform into the pseudonymization method
            f = getPseudonymize(pseudonymizer, (Transform.Pseudonymize) transform);
        } else if (transform instanceof Transform.PseudonymizeEmailHeader) {
            f = getPseudonymizeEmailHeaderToJson(pseudonymizer, (Transform.PseudonymizeEmailHeader) transform);
        } else if (transform instanceof Transform.PseudonymizeRegexMatches) {
            f = getPseudonymizeRegexMatches(pseudonymizer, (Transform.PseudonymizeRegexMatches) transform);
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

    /**
     * Applies a transform to a document, using the provided pseudonymizer.
     *
     * @param pseudonymizer to use
     * @param transform to apply
     * @param document will be MUTATED in place, if the transform applies
     * @param compiledTransforms pre-compiled JsonPaths for the transforms, to avoid re-compiling them if transform is already in that list
     */
    void applyTransform(Pseudonymizer pseudonymizer, Transform transform, Object document, Map<Transform, List<JsonPath>> compiledTransforms) {
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
                MapFunction f = getTransformImpl(pseudonymizer, transform);
                for (JsonPath path : paths) {
                    try {
                        path.map(document, f, jsonConfiguration);
                    } catch (com.jayway.jsonpath.PathNotFoundException e) {
                        //expected if rule doesn't apply
                    }
                }
            }
        }
    }

    private static boolean transformApplies(Transform transform, Object document) {
        if (StringUtils.isNotBlank(transform.getApplyOnlyWhen())) {
            Object filterResult = JsonPath.compile(transform.getApplyOnlyWhen()).read(document);

            ArrayList<?> results = (ArrayList<?>) filterResult;

            return results != null && !results.isEmpty();
        }
        return true;
    }
}
