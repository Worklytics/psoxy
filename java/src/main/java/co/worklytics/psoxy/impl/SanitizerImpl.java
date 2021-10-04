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

    Configuration jsonConfiguration;

    public void initConfiguration() {
        //jackson here because it's our common JSON stack, but adds dependency beyond the one pkg'd
        // with JsonPath.
        this.jsonConfiguration = Configuration.defaultConfiguration()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider());
    }

    @Override
    public String sanitize(GenericUrl url, String jsonResponse) {

        if (compiledPseudonymizations == null) {
            compiledPseudonymizations = compile(options.getPseudonymizations());
        }
        if (jsonConfiguration == null) {
            initConfiguration();
        }

        String relativeUrl = url.buildRelativeUrl();

        List<JsonPath> pseudonymizationsToApply = compiledPseudonymizations.stream()
            .filter(compiled -> compiled.getKey().asMatchPredicate().test(relativeUrl))
            .map(Pair::getValue)
            .flatMap(List::stream)
            .collect(Collectors.toList());

        if (pseudonymizationsToApply.isEmpty()) {
            return jsonResponse;
        } else {

            Object document = jsonConfiguration.jsonProvider().parse(jsonResponse);

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
        String valueToHash;
        //q: this auto-detect a good idea? Or invert control and let caller specify with a header
        // or something??
        if (value instanceof String && EmailAddressValidator.isValid((String) value)) {
            String domain = EmailAddressParser.getDomain((String) value, EmailAddressCriteria.DEFAULT, true);
            builder.domain(domain);
            valueToHash = EmailAddressParser.getLocalPart((String) value, EmailAddressCriteria.DEFAULT, true);
        } else {
            valueToHash = value.toString();
        }
        if (valueToHash != null) {
            builder.hash(DigestUtils.sha256Hex(valueToHash + options.getPseudonymizationSalt()));
        }
        return builder.build();
    }

    String pseudonymizeToJson(Object value, Configuration configuration) {
        return configuration.jsonProvider().toJson(pseudonymize(value));
    }



    private List<Pair<Pattern, List<JsonPath>>> compile(List<Pair<String, List<String>>> redactions) {
        return redactions.stream()
            .map(configured -> Pair.of(Pattern.compile(configured.getKey()),
                                       configured.getValue().stream()
                                           .map(JsonPath::compile)
                                           .collect(Collectors.toList())))
            .collect(Collectors.toList());
    }
}
