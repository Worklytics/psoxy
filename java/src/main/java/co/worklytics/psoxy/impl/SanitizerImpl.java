package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Sanitizer;
import com.google.api.client.http.GenericUrl;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class SanitizerImpl implements Sanitizer {

    final Options options;

    List<Pair<Pattern, List<JsonPath>>> compiledPseudonymizations;

    @Override
    public String sanitize(GenericUrl url, String jsonResponse) {

        if (compiledPseudonymizations == null) {
            compiledPseudonymizations = compile(options.getPseudonymizations());
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
            Configuration configuration = Configuration.defaultConfiguration();

            Object document = configuration.jsonProvider().parse(jsonResponse);

            for (JsonPath pseudonymization : pseudonymizationsToApply) {
                document = pseudonymization
                    .map(document, (s, config) -> DigestUtils.sha256Hex(s + "salt"), configuration);
            }

            return configuration.jsonProvider().toJson(document);
        }

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
