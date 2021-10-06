package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Pseudonym;
import co.worklytics.psoxy.Sanitizer;
import com.google.api.client.http.GenericUrl;
import com.google.common.base.Preconditions;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressCriteria;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressParser;
import org.hazlewood.connor.bottema.emailaddress.EmailAddressValidator;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class SanitizerImpl implements Sanitizer {

    final Options options;

    List<Pair<Pattern, List<JsonPath>>> compiledPseudonymizations;
    List<Pair<Pattern, List<JsonPath>>> compiledRedactions;

    Configuration jsonConfiguration;

    public void initConfiguration() {
        //jackson here because it's our common JSON stack, but adds dependency beyond the one pkg'd
        // with JsonPath.
        this.jsonConfiguration = Configuration.defaultConfiguration()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider());
    }

    List<JsonPath> applicablePaths(List<Pair<Pattern, List<JsonPath>>> rules, String relativeUrl) {
        return rules.stream()
            .filter(compiled -> compiled.getKey().asMatchPredicate().test(relativeUrl))
            .map(Pair::getValue)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    @Override
    public String sanitize(GenericUrl url, String jsonResponse) {

        if (compiledPseudonymizations == null) {
            compiledPseudonymizations = compile(options.getRules().getPseudonymizations());
        }
        if (compiledRedactions == null) {
            compiledRedactions = compile(options.getRules().getRedactions());
        }
        if (jsonConfiguration == null) {
            initConfiguration();
        }

        String relativeUrl = url.buildRelativeUrl();

        List<JsonPath> pseudonymizationsToApply =
            applicablePaths(compiledPseudonymizations, relativeUrl);


        List<JsonPath> redactionsToApply = applicablePaths(compiledRedactions, relativeUrl);

        if (pseudonymizationsToApply.isEmpty() && redactionsToApply.isEmpty()) {
            return jsonResponse;
        } else {
            Object document = jsonConfiguration.jsonProvider().parse(jsonResponse);

            for (JsonPath redaction : redactionsToApply) {
                document = redaction
                    .map(document, (n, config) -> null, jsonConfiguration);
            }

            for (JsonPath pseudonymization : pseudonymizationsToApply) {
                document = pseudonymization
                    .map(document, this::pseudonymizeToJson, jsonConfiguration);
            }

            return jsonConfiguration.jsonProvider().toJson(document);
        }
    }

    Pseudonym pseudonymize(Object value) {
        Preconditions.checkArgument(value instanceof String || value instanceof Number,
            "Value must be some basic type (eg JSON leaf, not node)");

        Pseudonym.PseudonymBuilder builder = Pseudonym.builder();
        String canonicalValue;
        //q: this auto-detect a good idea? Or invert control and let caller specify with a header
        // or something??
        if (value instanceof String && EmailAddressValidator.isValid((String) value)) {
            String domain = EmailAddressParser.getDomain((String) value, EmailAddressCriteria.DEFAULT, true);
            builder.domain(domain);

            //NOTE: lower-case here is NOT stipulated by RFC
            canonicalValue =
                EmailAddressParser.getLocalPart((String) value, EmailAddressCriteria.DEFAULT, true)
                    .toLowerCase()
                + "@"
                + domain.toLowerCase();
        } else {
            canonicalValue = value.toString();
        }
        if (canonicalValue != null) {
            builder.hash(DigestUtils.sha256Hex(canonicalValue + options.getPseudonymizationSalt()));
        }
        return builder.build();
    }

    String pseudonymizeToJson(Object value, Configuration configuration) {
        return configuration.jsonProvider().toJson(pseudonymize(value));
    }



    private List<Pair<Pattern, List<JsonPath>>> compile(List<Rules.Rule> redactions) {
        return redactions.stream()
            .map(configured -> Pair.of(Pattern.compile(configured.getRelativeUrlRegex()),
                                       configured.getJsonPaths().stream()
                                           .map(JsonPath::compile)
                                           .collect(Collectors.toList())))
            .collect(Collectors.toList());
    }
}
