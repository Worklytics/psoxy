package co.worklytics.psoxy.gateway.output;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.Optional;
import java.util.Set;

@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Value
public class OutputLocationImpl implements OutputLocation {

    String kind;

    String uri;

    public static OutputLocationImpl of(String uri) {

        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Output location URI must not be blank");
        }

        // as a convention, also do 'pubsub://` and `bq://` for Google Cloud Pub/Sub and BigQuery ??? why not
        Set<String> bucketKinds = Set.of("s3", "gs");
        Optional<String> bucketKind = bucketKinds.stream().filter(t -> uri.startsWith(t)).findAny();

        if (bucketKind.isPresent()) {
            return new OutputLocationImpl(bucketKind.get(), uri);
        }

        if (uri.startsWith("https://sqs.")) {
            return new OutputLocationImpl("sqs", uri);
        }

        throw new IllegalArgumentException("Output location URI could not be parsed: " + uri);
    }
}
